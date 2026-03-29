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

<p float="left">
  <img src="https://github.com/user-attachments/assets/221d7f42-c0a4-4d4f-b095-a1056c6752e5" width="200" />
  <img src="https://github.com/user-attachments/assets/bdfcae17-2d5e-4f0b-b689-1eadfbffbc41" width="200" /> 
  <img src="https://github.com/user-attachments/assets/1902d160-9960-4b5e-8bb6-4a8e1b7e94e8" width="200" />
  <img src="https://github.com/user-attachments/assets/519c92f4-ea7c-4af8-9d5a-59ddba357c4c" width="200" />
  <img src="https://github.com/user-attachments/assets/3f2f5325-e116-4eba-bfbf-722a5042abcf" width="200" />
 
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


### 2. Configuração do Firebase
* Crie um projeto no Firebase e adicione um aplicativo Android.
* Baixe o arquivo `google-services.json` e coloque-o dentro da pasta `/app` do projeto.
* Ative a autenticação pelo Google e crie um banco de dados no Firestore.

### 3. Configuração da Chave da IA (Secrets)
* Na raiz do projeto (na mesma pasta do `build.gradle.kts` principal), localize ou crie um arquivo chamado `local.properties`.
* Adicione a sua chave do Gemini neste arquivo:
  ```properties
  GEMINI_API_KEY=sua_chave_secreta_aqui

  Nota: O arquivo local.properties é ignorado pelo Git (via .gitignore) por questões de segurança, portanto, sua chave não será exposta ao público.

4. Sincronize e Rode
Clique em Sync Project with Gradle Files no Android Studio.

Vá em Build > Rebuild Project para gerar as classes de configuração (BuildConfig).

Execute o app no emulador ou dispositivo físico.

🤝 Contribuindo
Contribuições são muito bem-vindas! Se você tiver alguma ideia para melhorar o app:

Faça um Fork do projeto

Crie sua Feature Branch (git checkout -b feature/NovaFuncionalidade)

Faça o Commit das suas mudanças (git commit -m 'Adicionando uma Nova Funcionalidade')

Faça o Push para a Branch (git push origin feature/NovaFuncionalidade)

Abra um Pull Request

📄 Licença
Distribuído sob a licença MIT. Veja LICENSE para mais informações.

Desenvolvido com 💜 e Jetpack Compose por [Hudson](https://www.google.com/url?sa=E&source=gmail&q=https://github.com/hsrodrigues).
