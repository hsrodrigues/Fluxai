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
  // Fatura/extrato em PDF: lista longa de lançamentos, precisa de mais espaço na resposta
  extrato: { chave: GROQ_API_KEY_OCR, modelo: "openai/gpt-oss-20b", temperatura: 0.1, maxTokens: 8000, reasoning: "low" },
  // Conversa de acompanhamento com o Consultor: respostas curtas, histórico limitado
  chat: { chave: GROQ_API_KEY_CONSULTOR, modelo: "openai/gpt-oss-20b", temperatura: 0.6, maxTokens: 900, reasoning: "low", maxMensagens: 10 },
};

// Qualquer conta logada pode usar (não há mais ativação pelo administrador)
async function exigirContaAtiva(auth) {
  if (!auth) throw new HttpsError("unauthenticated", "Faça login para continuar.");
}

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
    await exigirContaAtiva(request.auth);

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
    await db.collection("acesso").doc(uid).delete();

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


// Cotações para a carteira de investimentos: B3 (ações, FIIs, ETFs) pelo Yahoo Finance e cripto pela AwesomeAPI.
// Pelo servidor para trocar a fonte sem atualizar o app. Cache de 5 min por ativo.
const cacheCotacoes = new Map();
const CACHE_COTACAO_MS = 5 * 60 * 1000;

async function cotacaoB3(ticker) {
  const url = `https://query1.finance.yahoo.com/v8/finance/chart/${encodeURIComponent(ticker)}.SA?range=1mo&interval=1d`;
  const resp = await fetch(url, { headers: { "User-Agent": "Mozilla/5.0" } });
  if (!resp.ok) return null;
  const r = (await resp.json())?.chart?.result?.[0];
  const preco = r?.meta?.regularMarketPrice;
  if (typeof preco !== "number") return null;
  // Último fechamento do mês anterior (base do "rendeu no mês") e do pregão anterior (variação do dia)
  const inicioMes = new Date(); inicioMes.setUTCDate(1); inicioMes.setUTCHours(0, 0, 0, 0);
  const tempos = r.timestamp || [];
  const fechamentos = r.indicators?.quote?.[0]?.close || [];
  const validos = fechamentos.filter((v) => typeof v === "number");
  const anterior = validos.length >= 2 ? validos[validos.length - 2] : r.meta.chartPreviousClose;
  let precoInicioMes = null;
  for (let i = 0; i < tempos.length; i++) {
    if (tempos[i] * 1000 < inicioMes.getTime() && typeof fechamentos[i] === "number") precoInicioMes = fechamentos[i];
  }
  return {
    preco,
    variacaoDia: typeof anterior === "number" && anterior > 0 ? (preco / anterior - 1) * 100 : 0,
    precoInicioMes: precoInicioMes ?? preco,
    nome: r.meta.longName || r.meta.shortName || ticker,
  };
}

async function cotacaoCripto(ticker) {
  const resp = await fetch(`https://economia.awesomeapi.com.br/json/daily/${encodeURIComponent(ticker)}-BRL/35`);
  if (!resp.ok) return null;
  const dias = await resp.json();
  if (!Array.isArray(dias) || dias.length === 0) return null;
  const preco = Number(dias[0].bid);
  if (!(preco > 0)) return null;
  const inicioMes = new Date(); inicioMes.setUTCDate(1); inicioMes.setUTCHours(0, 0, 0, 0);
  const antesDoMes = dias.find((d) => Number(d.timestamp) * 1000 < inicioMes.getTime());
  return {
    preco,
    variacaoDia: Number(dias[0].pctChange) || 0,
    precoInicioMes: antesDoMes ? Number(antesDoMes.bid) : preco,
    nome: (dias[0].name || ticker).split("/")[0],
  };
}

exports.cotacoes = onCall(
  { region: "southamerica-east1", timeoutSeconds: 30, memory: "256MiB" },
  async (request) => {
    await exigirContaAtiva(request.auth);
    const ativos = Array.isArray(request.data?.ativos) ? request.data.ativos.slice(0, 40) : [];
    const resultado = {};
    await Promise.all(ativos.map(async (a) => {
      const ticker = String(a?.ticker || "").toUpperCase().trim();
      const cripto = a?.tipo === "cripto";
      if (!/^[A-Z0-9]{2,12}$/.test(ticker)) return;
      const chave = `${cripto ? "c" : "b"}:${ticker}`;
      const guardado = cacheCotacoes.get(chave);
      if (guardado && Date.now() - guardado.em < CACHE_COTACAO_MS) { resultado[ticker] = guardado.dados; return; }
      try {
        const dados = cripto ? await cotacaoCripto(ticker) : await cotacaoB3(ticker);
        if (dados) { cacheCotacoes.set(chave, { em: Date.now(), dados }); resultado[ticker] = dados; }
      } catch (e) {
        console.error(`Cotação ${chave}: ${e.message}`);
      }
    }));
    return { cotacoes: resultado };
  }
);

// Fechamento de cada mês (últimos 12) para o gráfico de evolução do patrimônio. Cache de 6 h por ativo.
const cacheHistorico = new Map();
const CACHE_HISTORICO_MS = 6 * 60 * 60 * 1000;
const chaveMes = (ms) => new Date(ms).toISOString().slice(0, 7);

async function historicoB3(ticker) {
  const url = `https://query1.finance.yahoo.com/v8/finance/chart/${encodeURIComponent(ticker)}.SA?range=2y&interval=1mo`;
  const resp = await fetch(url, { headers: { "User-Agent": "Mozilla/5.0" } });
  if (!resp.ok) return null;
  const r = (await resp.json())?.chart?.result?.[0];
  if (!r) return null;
  const meses = {};
  const tempos = r.timestamp || [];
  const fechamentos = r.indicators?.quote?.[0]?.close || [];
  // Cada barra mensal fecha no último pregão do mês; a última barra é o preço mais recente
  tempos.forEach((t, i) => { if (typeof fechamentos[i] === "number") meses[chaveMes(t * 1000)] = fechamentos[i]; });
  return meses;
}

async function historicoCripto(ticker) {
  const resp = await fetch(`https://economia.awesomeapi.com.br/json/daily/${encodeURIComponent(ticker)}-BRL/360`);
  if (!resp.ok) return null;
  const dias = await resp.json();
  if (!Array.isArray(dias)) return null;
  const meses = {};
  // Lista vem do mais novo para o mais antigo: o primeiro de cada mês é o fechamento dele
  for (const d of dias) {
    const mes = chaveMes(Number(d.timestamp) * 1000);
    if (!(mes in meses) && Number(d.bid) > 0) meses[mes] = Number(d.bid);
  }
  return meses;
}

exports.historicoPrecos = onCall(
  { region: "southamerica-east1", timeoutSeconds: 30, memory: "256MiB" },
  async (request) => {
    await exigirContaAtiva(request.auth);
    const ativos = Array.isArray(request.data?.ativos) ? request.data.ativos.slice(0, 40) : [];
    const resultado = {};
    await Promise.all(ativos.map(async (a) => {
      const ticker = String(a?.ticker || "").toUpperCase().trim();
      const cripto = a?.tipo === "cripto";
      if (!/^[A-Z0-9]{2,12}$/.test(ticker)) return;
      const chave = `${cripto ? "c" : "b"}:${ticker}`;
      const guardado = cacheHistorico.get(chave);
      if (guardado && Date.now() - guardado.em < CACHE_HISTORICO_MS) { resultado[ticker] = guardado.dados; return; }
      try {
        const dados = cripto ? await historicoCripto(ticker) : await historicoB3(ticker);
        if (dados) { cacheHistorico.set(chave, { em: Date.now(), dados }); resultado[ticker] = dados; }
      } catch (e) {
        console.error(`Histórico ${chave}: ${e.message}`);
      }
    }));
    return { historico: resultado };
  }
);


// Pulso de nova versão: o publicar.ps1 chama esta URL depois de publicar o site.
// Lê o versao.json do site e, se for mais novo que o último aviso, manda um push ao tópico "atualizacao".
// Pode ser chamada por qualquer um sem efeito extra: só avisa uma vez por versão.
const { onRequest } = require("firebase-functions/v2/https");
const { getMessaging } = require("firebase-admin/messaging");

exports.avisarNovaVersao = onRequest(
  { region: "southamerica-east1", memory: "256MiB", timeoutSeconds: 30 },
  async (req, res) => {
    const resp = await fetch(`https://fluxai-adbdf.web.app/versao.json?t=${Date.now()}`);
    if (!resp.ok) { res.status(502).send("versao.json indisponível"); return; }
    const { versao, nome } = await resp.json();
    if (!versao) { res.status(502).send("versao.json inválido"); return; }

    const ref = getFirestore().collection("config").doc("aviso_versao");
    const ja = (await ref.get()).get("versao") || 0;
    if (versao <= ja) { res.send(`Versão ${versao} já avisada`); return; }
    await ref.set({ versao, em: FieldValue.serverTimestamp() });

    await getMessaging().send({
      topic: "atualizacao",
      data: { tipo: "nova_versao", versao: String(versao), nome: String(nome || versao) },
      android: { priority: "high" },
    });
    res.send(`Aviso enviado: versão ${versao}`);
  }
);
