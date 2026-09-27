// Proxy da IA do FluxAí: as chaves da Groq ficam no Secret Manager, nunca no APK.
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

initializeApp();

const GROQ_API_KEY_OCR = defineSecret("GROQ_API_KEY_OCR");
const GROQ_API_KEY_CONSULTOR = defineSecret("GROQ_API_KEY_CONSULTOR");

// Configuração fixa por tipo de uso: o app não escolhe modelo nem limites
const PERFIS = {
  ocr: { chave: GROQ_API_KEY_OCR, modelo: "openai/gpt-oss-20b", temperatura: 0.1, maxTokens: 1024 },
  consultor: { chave: GROQ_API_KEY_CONSULTOR, modelo: "openai/gpt-oss-20b", temperatura: 0.5, maxTokens: 1500, reasoning: "low" },
  // Conversa de acompanhamento com o Consultor: respostas curtas, histórico limitado
  chat: { chave: GROQ_API_KEY_CONSULTOR, modelo: "openai/gpt-oss-20b", temperatura: 0.6, maxTokens: 900, reasoning: "low", maxMensagens: 10 },
};

const LIMITE_DIARIO_POR_USUARIO = 60;
const MAX_CARACTERES = 20000;

// Conta as chamadas do dia e bloqueia quem passar do limite
async function registrarUso(uid) {
  const hoje = new Date().toISOString().slice(0, 10);
  const ref = getFirestore().collection("uso_ia").doc(`${uid}_${hoje}`);
  await getFirestore().runTransaction(async (tx) => {
    const doc = await tx.get(ref);
    const total = doc.exists ? doc.get("total") || 0 : 0;
    if (total >= LIMITE_DIARIO_POR_USUARIO) {
      throw new HttpsError("resource-exhausted", "Limite diário de uso da IA atingido. Tente amanhã.");
    }
    tx.set(ref, { uid, dia: hoje, total: FieldValue.increment(1) }, { merge: true });
  });
}

exports.groqChat = onCall(
  { region: "southamerica-east1", secrets: [GROQ_API_KEY_OCR, GROQ_API_KEY_CONSULTOR], timeoutSeconds: 60, memory: "256MiB" },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Faça login para usar a IA.");

    const { tipo, mensagens } = request.data || {};
    const perfil = PERFIS[tipo];
    if (!perfil) throw new HttpsError("invalid-argument", "Tipo de chamada inválido.");

    // Aceita só mensagens system/user com texto, dentro do limite de tamanho
    if (!Array.isArray(mensagens) || mensagens.length === 0 || mensagens.length > (perfil.maxMensagens || 4)) {
      throw new HttpsError("invalid-argument", "Mensagens inválidas.");
    }
    const limpas = mensagens.map((m) => ({
      role: m && ["system", "assistant"].includes(m.role) ? m.role : "user",
      content: String((m && m.content) || ""),
    }));
    const tamanho = limpas.reduce((t, m) => t + m.content.length, 0);
    if (tamanho === 0 || tamanho > MAX_CARACTERES) throw new HttpsError("invalid-argument", "Texto vazio ou grande demais.");

    await registrarUso(request.auth.uid);

    const corpo = {
      model: perfil.modelo,
      temperature: perfil.temperatura,
      max_tokens: perfil.maxTokens,
      messages: limpas,
    };
    if (perfil.reasoning) corpo.reasoning_effort = perfil.reasoning;

    const resp = await fetch("https://api.groq.com/openai/v1/chat/completions", {
      method: "POST",
      headers: { Authorization: `Bearer ${perfil.chave.value()}`, "Content-Type": "application/json" },
      body: JSON.stringify(corpo),
    });

    if (!resp.ok) {
      const erro = await resp.text();
      console.error(`Groq HTTP ${resp.status}: ${erro}`);
      let msg = `Falha na IA (${resp.status})`;
      try { msg += `: ${JSON.parse(erro).error.message}`; } catch (_) { /* resposta sem JSON */ }
      throw new HttpsError("unavailable", msg);
    }

    const json = await resp.json();
    return { conteudo: json?.choices?.[0]?.message?.content || "" };
  }
);

// Exclusão de conta (LGPD): apaga TODOS os dados do usuário, inclusive subcoleções, e depois o login.
// Feito no servidor porque o Firestore não apaga subcoleções ao excluir um documento pelo app.
const { getAuth } = require("firebase-admin/auth");
const { getStorage } = require("firebase-admin/storage");

exports.excluirConta = onCall(
  { region: "southamerica-east1", timeoutSeconds: 300, memory: "256MiB" },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Faça login para excluir a conta.");
    const uid = request.auth.uid;
    const db = getFirestore();

    // Mesma proteção do Firebase no app: exige login recente para uma ação irreversível
    const minutosDesdeLogin = (Date.now() / 1000 - request.auth.token.auth_time) / 60;
    if (minutosDesdeLogin > 10) {
      throw new HttpsError("failed-precondition", "Por segurança, saia da conta, entre novamente e repita a exclusão.");
    }

    await db.recursiveDelete(db.collection("usuarios").doc(uid));

    // Fotos de comprovantes guardadas no Storage
    await getStorage().bucket().deleteFiles({ prefix: `usuarios/${uid}/` }).catch((e) => console.error("Falha ao apagar comprovantes", e));

    const lotes = [
      await db.collection("convites").where("dono", "==", uid).get(),
      await db.collection("uso_ia").where("uid", "==", uid).get(),
    ];
    for (const snap of lotes) {
      const batch = db.batch();
      snap.docs.forEach((d) => batch.delete(d.ref));
      if (!snap.empty) await batch.commit();
    }

    await getAuth().deleteUser(uid);
    return { ok: true };
  }
);
