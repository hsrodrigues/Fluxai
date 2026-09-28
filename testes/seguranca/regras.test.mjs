// Testes de segurança das regras do Firestore e do Storage (rodam no emulador).
// Cada perfil tenta o que pode e o que não pode: sem login, pendente, ativo, membro, admin e admin falso.
import { test, before, after, beforeEach, describe } from "node:test";
import { readFileSync } from "node:fs";
import { initializeTestEnvironment, assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc, deleteDoc, getDocs, collection, Timestamp } from "firebase/firestore";
import { ref, uploadBytes, getBytes } from "firebase/storage";

const ADMIN = "hsrodrigues01@gmail.com";
let env;

// Perfis de teste
const perfis = {
  anonimo: () => env.unauthenticatedContext(),
  pendente: () => env.authenticatedContext("pend", { email: "pend@teste.com", email_verified: true }),
  ativo: () => env.authenticatedContext("ativo", { email: "ativo@teste.com", email_verified: true }),
  outro: () => env.authenticatedContext("outro", { email: "outro@teste.com", email_verified: true }),
  membro: () => env.authenticatedContext("membro", { email: "membro@teste.com", email_verified: true }),
  membroPendente: () => env.authenticatedContext("membroPend", { email: "mp@teste.com", email_verified: true }),
  novo: () => env.authenticatedContext("novo", { email: "novo@teste.com", email_verified: true }),
  admin: () => env.authenticatedContext("admin", { email: ADMIN, email_verified: true }),
  // Mesmo e-mail do admin, mas sem verificação (ex.: conta e-mail/senha criada por outra pessoa)
  adminFalso: () => env.authenticatedContext("adminFalso", { email: ADMIN, email_verified: false }),
};
const db = (p) => perfis[p]().firestore();
const st = (p) => perfis[p]().storage();
const daquiDias = (d) => Timestamp.fromMillis(Date.now() + d * 86400000);

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-fluxai",
    firestore: { rules: readFileSync("../../firestore.rules", "utf8"), host: "127.0.0.1", port: 8080 },
    storage: { rules: readFileSync("../../storage.rules", "utf8"), host: "127.0.0.1", port: 9199 },
  });
});
after(async () => { await env?.cleanup(); });

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const d = ctx.firestore();
    const acesso = { pend: false, ativo: true, outro: true, membro: true, membroPend: false };
    for (const [uid, ativo] of Object.entries(acesso)) {
      await setDoc(doc(d, "acesso", uid), { nome: uid, email: `${uid}@teste.com`, ativo, criadoEm: Timestamp.now() });
    }
    for (const uid of ["pend", "ativo", "outro"]) {
      await setDoc(doc(d, "usuarios", uid), { nome: uid });
      await setDoc(doc(d, "usuarios", uid, "despesas", "d1"), { valor: 10 });
    }
    // Conta conjunta do "ativo": um membro ativo e um membro pendente
    await setDoc(doc(d, "usuarios", "ativo", "membros", "membro"), { codigo: "X" });
    await setDoc(doc(d, "usuarios", "ativo", "membros", "membroPend"), { codigo: "X" });
    await setDoc(doc(d, "convites", "C1"), { dono: "ativo", expiraEm: daquiDias(1) });
    await setDoc(doc(d, "convites", "VENCIDO"), { dono: "ativo", expiraEm: daquiDias(-1) });
    await setDoc(doc(d, "app_config", "geral"), { versao: 1 });
    await setDoc(doc(d, "uso_ia", "ativo_2026-09-28"), { uid: "ativo", total: 60 });
  });
});

describe("Sem login", () => {
  test("não lê dados de usuário", () => assertFails(getDoc(doc(db("anonimo"), "usuarios/ativo/despesas/d1"))));
  test("não lê acesso", () => assertFails(getDoc(doc(db("anonimo"), "acesso/ativo"))));
  test("não lê app_config", () => assertFails(getDoc(doc(db("anonimo"), "app_config/geral"))));
  test("não cria registro de acesso", () => assertFails(setDoc(doc(db("anonimo"), "acesso/x"), { nome: "", email: "", ativo: false, criadoEm: Timestamp.now() })));
});

describe("Conta pendente (não ativada)", () => {
  test("não lê os próprios dados", () => assertFails(getDoc(doc(db("pendente"), "usuarios/pend/despesas/d1"))));
  test("não grava os próprios dados", () => assertFails(setDoc(doc(db("pendente"), "usuarios/pend/despesas/d2"), { valor: 1 })));
  test("não lê dados de outro", () => assertFails(getDoc(doc(db("pendente"), "usuarios/ativo/despesas/d1"))));
  test("lê o próprio registro de acesso", () => assertSucceeds(getDoc(doc(db("pendente"), "acesso/pend"))));
  test("não lê o acesso de outro", () => assertFails(getDoc(doc(db("pendente"), "acesso/ativo"))));
  test("não lista a coleção de acesso", () => assertFails(getDocs(collection(db("pendente"), "acesso"))));
  test("não se autoativa (update)", () => assertFails(updateDoc(doc(db("pendente"), "acesso/pend"), { ativo: true })));
  test("não se autoativa (set por cima)", () => assertFails(setDoc(doc(db("pendente"), "acesso/pend"), { nome: "", email: "", ativo: true, criadoEm: Timestamp.now() })));
  test("não apaga o próprio registro para recriar", () => assertFails(deleteDoc(doc(db("pendente"), "acesso/pend"))));
  test("não lê app_config", () => assertFails(getDoc(doc(db("pendente"), "app_config/geral"))));
  test("não cria convite", () => assertFails(setDoc(doc(db("pendente"), "convites/P1"), { dono: "pend", expiraEm: daquiDias(1) })));
  test("não lê convite válido", () => assertFails(getDoc(doc(db("pendente"), "convites/C1"))));
  test("não entra em conta conjunta com convite válido", () =>
    assertFails(setDoc(doc(db("pendente"), "usuarios/ativo/membros/pend"), { codigo: "C1" })));
});

describe("Cadastro novo", () => {
  test("cria o próprio registro como pendente", () =>
    assertSucceeds(setDoc(doc(db("novo"), "acesso/novo"), { nome: "Novo", email: "novo@teste.com", ativo: false, criadoEm: Timestamp.now() })));
  test("não cria o próprio registro já ativo", () =>
    assertFails(setDoc(doc(db("novo"), "acesso/novo"), { nome: "Novo", email: "novo@teste.com", ativo: true, criadoEm: Timestamp.now() })));
  test("não cria registro com campos extras (ex.: admin)", () =>
    assertFails(setDoc(doc(db("novo"), "acesso/novo"), { nome: "", email: "", ativo: false, criadoEm: Timestamp.now(), admin: true })));
  test("não cria registro de outra pessoa", () =>
    assertFails(setDoc(doc(db("novo"), "acesso/outraPessoa"), { nome: "", email: "", ativo: false, criadoEm: Timestamp.now() })));
  test("não sobrescreve registro existente de outro", () =>
    assertFails(setDoc(doc(db("novo"), "acesso/ativo"), { nome: "", email: "", ativo: false, criadoEm: Timestamp.now() })));
});

describe("Conta ativa", () => {
  test("lê os próprios dados", () => assertSucceeds(getDoc(doc(db("ativo"), "usuarios/ativo/despesas/d1"))));
  test("grava os próprios dados", () => assertSucceeds(setDoc(doc(db("ativo"), "usuarios/ativo/despesas/d2"), { valor: 5 })));
  test("não lê dados de outra conta", () => assertFails(getDoc(doc(db("ativo"), "usuarios/outro/despesas/d1"))));
  test("não grava dados de outra conta", () => assertFails(setDoc(doc(db("ativo"), "usuarios/outro/despesas/x"), { valor: 1 })));
  test("não lê o documento raiz de outra conta", () => assertFails(getDoc(doc(db("ativo"), "usuarios/outro"))));
  test("não lista a coleção de acesso", () => assertFails(getDocs(collection(db("ativo"), "acesso"))));
  test("não ativa outra conta", () => assertFails(updateDoc(doc(db("ativo"), "acesso/pend"), { ativo: true })));
  test("não altera o próprio acesso", () => assertFails(updateDoc(doc(db("ativo"), "acesso/ativo"), { nome: "x" })));
  test("lê app_config", () => assertSucceeds(getDoc(doc(db("ativo"), "app_config/geral"))));
  test("não grava app_config", () => assertFails(setDoc(doc(db("ativo"), "app_config/geral"), { versao: 999 })));
  test("não zera o contador de uso da IA", () => assertFails(setDoc(doc(db("ativo"), "uso_ia/ativo_2026-09-28"), { uid: "ativo", total: 0 })));
  test("não lê o contador de uso da IA", () => assertFails(getDoc(doc(db("ativo"), "uso_ia/ativo_2026-09-28"))));
  test("não se coloca como membro de outra conta sem convite", () =>
    assertFails(setDoc(doc(db("outro"), "usuarios/ativo/membros/outro"), { codigo: "INEXISTENTE" })));
  test("não entra com convite vencido", () =>
    assertFails(setDoc(doc(db("outro"), "usuarios/ativo/membros/outro"), { codigo: "VENCIDO" })));
  test("entra com convite válido", () =>
    assertSucceeds(setDoc(doc(db("outro"), "usuarios/ativo/membros/outro"), { codigo: "C1" })));
  test("não adiciona outra pessoa como membro", () =>
    assertFails(setDoc(doc(db("outro"), "usuarios/ativo/membros/terceiro"), { codigo: "C1" })));
});

describe("Convites", () => {
  test("ativo cria convite próprio com validade curta", () =>
    assertSucceeds(setDoc(doc(db("ativo"), "convites/A1"), { dono: "ativo", expiraEm: daquiDias(1) })));
  test("não cria convite em nome de outro", () =>
    assertFails(setDoc(doc(db("ativo"), "convites/A2"), { dono: "outro", expiraEm: daquiDias(1) })));
  test("não cria convite com validade longa (30 dias)", () =>
    assertFails(setDoc(doc(db("ativo"), "convites/A3"), { dono: "ativo", expiraEm: daquiDias(30) })));
  test("não lista convites", () => assertFails(getDocs(collection(db("outro"), "convites"))));
  test("não apaga convite de outro", () => assertFails(deleteDoc(doc(db("outro"), "convites/C1"))));
});

describe("Conta conjunta", () => {
  test("membro ativo lê os dados do dono", () => assertSucceeds(getDoc(doc(db("membro"), "usuarios/ativo/despesas/d1"))));
  test("membro ativo grava nos dados do dono", () => assertSucceeds(setDoc(doc(db("membro"), "usuarios/ativo/despesas/m1"), { valor: 3 })));
  test("membro pendente não lê os dados do dono", () => assertFails(getDoc(doc(db("membroPendente"), "usuarios/ativo/despesas/d1"))));
  test("membro não adiciona outra pessoa à conta", () =>
    assertFails(setDoc(doc(db("membro"), "usuarios/ativo/membros/intruso"), { codigo: "C1" })));
  test("membro não lê dados de terceiros", () => assertFails(getDoc(doc(db("membro"), "usuarios/outro/despesas/d1"))));
});

describe("Administrador", () => {
  test("lista as contas", () => assertSucceeds(getDocs(collection(db("admin"), "acesso"))));
  test("ativa uma conta", () => assertSucceeds(updateDoc(doc(db("admin"), "acesso/pend"), { ativo: true })));
  test("desativa uma conta", () => assertSucceeds(updateDoc(doc(db("admin"), "acesso/ativo"), { ativo: false })));
  test("grava app_config", () => assertSucceeds(setDoc(doc(db("admin"), "app_config/geral"), { versao: 2 })));
  test("admin falso (e-mail não verificado) não lista contas", () => assertFails(getDocs(collection(db("adminFalso"), "acesso"))));
  test("admin falso não ativa contas", () => assertFails(updateDoc(doc(db("adminFalso"), "acesso/pend"), { ativo: true })));
  test("admin falso não grava app_config", () => assertFails(setDoc(doc(db("adminFalso"), "app_config/geral"), { versao: 3 })));
  test("conta desativada perde o acesso na hora", async () => {
    await assertSucceeds(updateDoc(doc(db("admin"), "acesso/outro"), { ativo: false }));
    await assertFails(getDoc(doc(db("outro"), "usuarios/outro/despesas/d1")));
  });
});

describe("Storage (comprovantes)", () => {
  const img = new Uint8Array([0xff, 0xd8, 0xff, 0xe0]);
  const caminho = (uid, nome = "a.jpg") => `usuarios/${uid}/comprovantes/${nome}`;
  test("ativo envia imagem própria", () => assertSucceeds(uploadBytes(ref(st("ativo"), caminho("ativo")), img, { contentType: "image/jpeg" })));
  test("ativo envia PDF próprio", () => assertSucceeds(uploadBytes(ref(st("ativo"), caminho("ativo", "b.pdf")), img, { contentType: "application/pdf" })));
  test("não envia executável/APK", () =>
    assertFails(uploadBytes(ref(st("ativo"), caminho("ativo", "x.apk")), img, { contentType: "application/vnd.android.package-archive" })));
  test("não envia HTML (risco de página falsa)", () =>
    assertFails(uploadBytes(ref(st("ativo"), caminho("ativo", "x.html")), img, { contentType: "text/html" })));
  test("não envia arquivo acima de 10 MB", () =>
    assertFails(uploadBytes(ref(st("ativo"), caminho("ativo", "g.jpg")), new Uint8Array(10 * 1024 * 1024 + 1), { contentType: "image/jpeg" })));
  test("pendente não envia", () => assertFails(uploadBytes(ref(st("pendente"), caminho("pend")), img, { contentType: "image/jpeg" })));
  test("não envia na pasta de outra conta", () => assertFails(uploadBytes(ref(st("outro"), caminho("ativo", "o.jpg")), img, { contentType: "image/jpeg" })));
  test("sem login não envia", () => assertFails(uploadBytes(ref(st("anonimo"), caminho("ativo", "n.jpg")), img, { contentType: "image/jpeg" })));
  test("membro ativo lê comprovante do dono", async () => {
    await env.withSecurityRulesDisabled((ctx) => uploadBytes(ref(ctx.storage(), caminho("ativo", "m.jpg")), img, { contentType: "image/jpeg" }));
    await assertSucceeds(getBytes(ref(st("membro"), caminho("ativo", "m.jpg"))));
  });
  test("outra conta não lê comprovante", async () => {
    await env.withSecurityRulesDisabled((ctx) => uploadBytes(ref(ctx.storage(), caminho("ativo", "p.jpg")), img, { contentType: "image/jpeg" }));
    await assertFails(getBytes(ref(st("outro"), caminho("ativo", "p.jpg"))));
  });
  test("fora da pasta de comprovantes nada é permitido", () =>
    assertFails(uploadBytes(ref(st("ativo"), "publico/x.jpg"), img, { contentType: "image/jpeg" })));
});
