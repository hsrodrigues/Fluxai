# Política de Segurança (Security Policy)

A segurança do **FluxAí** é uma prioridade. Este documento define como relatar vulnerabilidades e as práticas de segurança esperadas dos contribuidores.

## Versões Suportadas

Atualmente, apenas a versão mais recente e ativa do aplicativo recebe atualizações de segurança.

| Versão | Atualizações de Segurança |
| ------ | ------------------------- |
| `main` | :white_check_mark: Sim    |
| Outras | :x: Não                   |

## 🚨 Como Reportar uma Vulnerabilidade

Se você descobrir um problema ou falha de segurança no código, por favor, **não crie uma *Issue* pública**. A exposição imediata pode colocar os dados dos usuários em risco.

Em vez disso, relate a vulnerabilidade de forma privada enviando um e-mail para:
📧 **[rodriguesstech@gmail.com]**

No seu e-mail, inclua:
1. Uma descrição clara do problema.
2. Passos detalhados para reproduzir a vulnerabilidade.
3. O impacto potencial (ex: vazamento de dados, quebra de autenticação).

Você receberá uma confirmação de recebimento em até 48 horas. Tentarei corrigir o problema o mais rápido possível e lançar uma atualização segura.

## ⚠️ Notas Importantes para Contribuidores

O FluxAí utiliza serviços em nuvem (Firebase) e Inteligência Artificial (Google Gemini). Para manter o repositório seguro, certifique-se de **absolutamente nunca** incluir os seguintes arquivos nos seus *commits*:

* `local.properties`: Este arquivo guarda a chave secreta da API do Gemini (`GEMINI_API_KEY`).
* `app/google-services.json`: Este arquivo guarda as configurações do seu projeto do Firebase.

Estes arquivos já estão configurados no `.gitignore` oficial do projeto. Sempre revise seus arquivos com `git status` antes de executar um `git push`.
