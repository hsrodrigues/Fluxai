# 🚀 FluxAí - Gestão Financeira Inteligente

![Kotlin](https://img.shields.io/badge/Kotlin-B125EA?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=android&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)
![Groq](https://img.shields.io/badge/IA_via_Groq-F55036?style=for-the-badge&logoColor=white)

O **FluxAí** é um aplicativo Android nativo para controle financeiro pessoal e familiar. Além de registrar gastos, ele projeta como o mês vai terminar, avisa sobre vencimentos e tem um consultor de IA que analisa os números do mês e responde perguntas sobre eles.

## ✨ Funcionalidades

### Dia a dia
* **📊 Dashboard do mês:** renda projetada, quanto falta pagar, quanto já foi pago e a sobra final, separados por quinzena (vale/adiantamento e salário).
* **🔮 Análise preditiva:** calcula o ritmo diário de gastos, estima a sobra no fim do mês, mostra quanto dá para gastar por dia e em que dia a sobra zera.
* **➕ Novo lançamento:** valor em destaque, categorias com ícones, parcelamento, cartão ou conta, e **leitura de nota fiscal/boleto pela câmera** (OCR + IA).
* **🔁 Contas recorrentes:** sugere trazer as contas fixas e as adiadas do mês anterior.
* **🏷️ Logos automáticos:** reconhece serviços pela descrição (Netflix, Spotify, Nubank, operadoras...) e mostra a bandeira do cartão usado na compra.
* **🔎 Busca, filtro por categoria e reordenação** dos lançamentos arrastando.

### Gestão
* **💳 Cartões:** limite, fatura atual e pagamento de fatura (inclusive parcial, com o restante lançado no mês seguinte).
* **🔄 Hub de assinaturas:** custo fixo mensal e anual, contas que vencem em breve e atrasadas.
* **📱 Planos e celular**, **🐷 Cofre/Metas (caixinhas)** e **🚗 Ativos & TCO** (custo total e manutenção de bens).
* **🏦 Empréstimos:** registra o valor recebido e gera as parcelas automaticamente.
* **📈 Análise BI:** composição de gastos, fixas vs. variáveis, progresso de pagamentos e maiores gastos.

### Inteligência e avisos
* **🤖 Consultor IA:** diagnóstico do mês com ações concretas em R$, seguido de um chat para tirar dúvidas usando os mesmos dados.
* **🔔 Alertas de vencimento** por notificação e aviso de contas que vencem em **feriado bancário** (Brasil API).
* **💱 Câmbio e mercado:** dólar, euro e bitcoin (AwesomeAPI).
* **🧩 Widget na tela inicial** com sobra do mês, limite diário e próximas contas.

### Conta e segurança
* **🔒 Login** com e-mail/senha ou Google (Credential Manager + Firebase Auth) e **desbloqueio por biometria** ou PIN do aparelho.
* **👨‍👩‍👧 Conta conjunta:** o dono gera um código de convite e a família compartilha o mesmo espaço financeiro.
* **🎨 Tema claro/escuro** e cor de destaque personalizável.
* **📂 Exportação** do mês em `.csv` e aviso de nova versão do app.

## 🛠️ Tecnologias

* **Linguagem:** [Kotlin](https://kotlinlang.org/)
* **UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose) com Material Design 3
* **Arquitetura:** Compose Navigation + ViewModels com `StateFlow`
* **Backend:** [Firebase](https://firebase.google.com/)
  * *Firestore* (dados em tempo real, protegidos por `firestore.rules`)
  * *Authentication* (e-mail/senha e Google)
  * *Cloud Functions* (proxy da IA e exclusão de conta) com *Secret Manager*
  * *Cloud Messaging* (notificações)
* **IA:** modelos via [Groq](https://groq.com/), chamados só pela Cloud Function `groqChat`, então **nenhuma chave de IA vai dentro do APK**
* **OCR:** ML Kit Text Recognition
* **Segundo plano:** WorkManager (alertas de vencimento e manutenção)
* **Widget:** Jetpack Glance
* **Gráficos:** desenhados com o `Canvas` do Compose

## 📱 Telas do Aplicativo

<p float="left">
  <img src="https://github.com/user-attachments/assets/221d7f42-c0a4-4d4f-b095-a1056c6752e5" width="200" />
  <img src="https://github.com/user-attachments/assets/bdfcae17-2d5e-4f0b-b689-1eadfbffbc41" width="200" />
  <img src="https://github.com/user-attachments/assets/1902d160-9960-4b5e-8bb6-4a8e1b7e94e8" width="200" />
  <img src="https://github.com/user-attachments/assets/519c92f4-ea7c-4af8-9d5a-59ddba357c4c" width="200" />
  <img src="https://github.com/user-attachments/assets/3f2f5325-e116-4eba-bfbf-722a5042abcf" width="200" />
</p>

## ⚙️ Como rodar o projeto localmente

### Pré-requisitos
* Android Studio (versão recente) com JDK 17
* Conta no [Firebase Console](https://console.firebase.google.com/) no plano Blaze (necessário para Cloud Functions com secrets)
* Chave de API da [Groq](https://console.groq.com/)
* [Firebase CLI](https://firebase.google.com/docs/cli) e Node.js, para publicar as functions

### 1. Clone o repositório
```bash
git clone https://github.com/hsrodrigues/Fluxai.git
```

### 2. Configure o Firebase
* Crie um projeto no Firebase e adicione um app Android com o pacote `fluxai.app`.
* Baixe o `google-services.json` e coloque em `app/`.
* Ative a autenticação por e-mail/senha e Google, e crie o banco Firestore.
* Publique as regras do banco:
  ```bash
  firebase deploy --only firestore:rules
  ```

### 3. Configure a IA (Cloud Function)
As chaves ficam no Secret Manager do Firebase, fora do app:
```bash
cd functions && npm install && cd ..
firebase functions:secrets:set GROQ_API_KEY_OCR
firebase functions:secrets:set GROQ_API_KEY_CONSULTOR
firebase deploy --only functions
```

> `google-services.json`, `local.properties` e keystores estão no `.gitignore` e nunca devem ir para o repositório.

### 4. Compile e rode
* No Android Studio, clique em **Sync Project with Gradle Files** e rode no emulador ou no aparelho.
* Pela linha de comando:
  ```bash
  ./gradlew installDebug          # versão de desenvolvimento
  ./gradlew assembleRelease       # versão final otimizada (R8) em app/build/outputs/apk/release/
  ./gradlew testDebugUnitTest     # testes unitários
  ```
* O número da versão é incrementado automaticamente a cada build (`app/version.properties`).

## 🤝 Contribuindo

1. Faça um fork do projeto
2. Crie sua branch (`git checkout -b feature/NovaFuncionalidade`)
3. Faça o commit (`git commit -m 'Adiciona nova funcionalidade'`)
4. Faça o push (`git push origin feature/NovaFuncionalidade`)
5. Abra um Pull Request

## 📄 Licença

Distribuído sob a licença MIT. Veja [LICENSE](LICENSE) para mais informações.

Desenvolvido com 💜 e Jetpack Compose por [Hudson](https://github.com/hsrodrigues).
