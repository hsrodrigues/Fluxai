# 🚀 FluxAí - Gestão Financeira Inteligente

![Kotlin](https://img.shields.io/badge/Kotlin-B125EA?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=android&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)
![Gemini AI](https://img.shields.io/badge/Gemini_AI-8E75B2?style=for-the-badge&logo=googlebard&logoColor=white)

O **FluxAí** é um aplicativo Android nativo criado para simplificar o controle financeiro pessoal. Muito além de uma simples planilha de gastos, ele integra Inteligência Artificial para atuar como um consultor financeiro de bolso, analisando sua renda e despesas para fornecer dicas personalizadas de economia e gestão.

## ✨ Principais Funcionalidades

* **🔒 Autenticação Rápida:** Login seguro e com um clique usando o Google (Credential Manager + Firebase Auth).
* **📊 Dashboard Intuitivo:** Visão geral da sua renda, contas a pagar, contas pagas e a "Sobra Real" do mês.
* **🤖 Consultoria Inteligente (IA):** Integração com a API do Google Gemini para ler o contexto do seu mês e gerar conselhos financeiros sob medida.
* **🔁 Importação de Contas Fixas:** Puxa automaticamente todas as contas marcadas como "Fixas" do mês anterior com apenas um clique.
* **📈 Business Intelligence (BI):** Uma tela dedicada de *Analytics* com gráficos de composição de gastos, proporção de fixas vs variáveis, progresso de pagamentos e o "Top 3 Maiores Gastos".
* **📂 Exportação de Dados:** Geração de relatórios mensais em formato `.csv` prontos para compartilhar.
* **📅 Navegação Retroativa:** Adicione, edite ou visualize despesas de qualquer mês ou ano de forma simples.

## 🛠️ Tecnologias Utilizadas

* **Linguagem:** [Kotlin](https://kotlinlang.org/)
* **UI Toolkit:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material Design 3)
* **Arquitetura/Navegação:** Compose Navigation
* **Backend as a Service:** [Firebase](https://firebase.google.com/)
  * *Firestore* (Banco de dados NoSQL em tempo real)
  * *Authentication* (Gerenciamento de usuários)
* **Inteligência Artificial:** Google Gemini API (`gemini-2.5-flash`)
* **Gráficos:** Desenho customizado via `Canvas` do Compose.

## 📱 Telas do Aplicativo

*(Adicione aqui os prints das telas do seu app. Exemplo de formato:)*
<p float="left">
  <img src="link_para_print_da_splash.png" width="200" />
  <img src="link_para_print_do_login.png" width="200" /> 
  <img src="link_para_print_do_dashboard.png" width="200" />
  <img src="link_para_print_do_analytics.png" width="200" />
</p>

## ⚙️ Como rodar o projeto localmente

Siga as instruções abaixo para compilar e testar o app na sua máquina:

### Pré-requisitos
* Android Studio (versão mais recente recomendada).
* Conta no [Firebase Console](https://console.firebase.google.com/).
* Chave de API do [Google AI Studio (Gemini)](https://aistudio.google.com/).

### Passos de Instalação
1. **Clone o repositório:**
   ```bash
   git clone [https://github.com/hsrodrigues/fluxai.git](https://github.com/SEU_USUARIO/fluxai.git)
1. **Clone o repositório:**
   ```bash
   git clone [https://github.com/SEU_USUARIO/fluxai.git](https://github.com/SEU_USUARIO/fluxai.git)
