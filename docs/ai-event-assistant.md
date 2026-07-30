# Assistente de rascunhos de eventos

## Objetivo e fluxo

O assistente transforma uma descrição em linguagem natural em um rascunho editável de evento. Ele nunca salva o evento: o usuário revisa todos os campos no modal e somente o botão normal **Agendar Encontro** confirma a criação.

```text
Events.tsx → POST /api/ai/event-drafts → AiEventDraftService
           → catálogo relacional de jogos → LiteLlmClient → LiteLLM
           ← rascunho validado no backend ← JSON do modelo
```

## Endpoint

`POST /ai/event-drafts`, com `Authorization: Bearer <token de sessão>`.

Request:

```json
{"prompt":"Planeje uma mesa de Xadrez sexta às 18h para seis pessoas."}
```

Response:

```json
{
  "gameId": "g1",
  "gameName": "Xadrez",
  "date": "2026-08-07",
  "time": "18:00",
  "location": "Biblioteca",
  "maxParticipants": 6,
  "description": "Mesa de Xadrez aberta para seis participantes.",
  "warnings": []
}
```

Erros esperados: `400` para prompt inválido, `401` para sessão inválida, `422` para saída da IA não validável, `429` para limite do provedor, `502` para resposta externa inválida e `503` para configuração ausente ou indisponibilidade transitória.

## Configuração

Somente o backend lê:

```env
LITELLM_API_KEY=<CHAVE_LITELLM_DA_EQUIPE>
LITELLM_BASE_URL=https://llm.rodrigor.com
LITELLM_MODEL=gpt-4o-mini
AI_REQUEST_TIMEOUT_SECONDS=15
APP_TIME_ZONE=America/Sao_Paulo
```

O modelo padrão é `gpt-4o-mini`, com temperatura `0.2`, resposta JSON e limite pequeno de tokens. Não existe variável `VITE_LITELLM_API_KEY`.

## Validações e segurança

O prompt é aparado e deve ter de 5 a 1000 caracteres. O system prompt trata o texto como dado não confiável, ignora tentativas de mudar instruções e proíbe SQL, comandos, HTML e markdown.

São enviados à LLM no máximo 200 jogos, somente com ID externo, nome, categoria, mínimo/máximo de jogadores, duração média e complexidade. Nunca são enviados usuários, e-mails, credenciais, tokens, comentários, sessões, eventos, auditoria ou o estado completo.

A saída precisa conter exatamente um objeto JSON. O backend verifica jogo e nome contra o banco, data/fuso, horário, limites globais e do jogo, localização, descrição e warnings. Campos desconhecidos são ignorados pelo parser, mas não influenciam o rascunho.

## Confiabilidade, auditoria e logs

O cliente reutiliza `HttpClient`, aplica timeout e faz no máximo uma tentativa adicional para timeout, conexão, `429` ou `5xx`; não repete `400`, `401` ou `403`. Interrupções de thread são preservadas.

A auditoria registra apenas modelo, comprimento do prompt, gameId, quantidade de warnings, duração, sucesso e categoria genérica de falha. Prompt, resposta bruta, chave e Authorization nunca são auditados ou logados. A falha da auditoria não altera a resposta principal.

## Testes e chamada real

Os testes usam clientes falsos e servidores HTTP locais; nunca consomem a LiteLLM. Execute:

```bash
cd backend
mvn test
cd ..
npm test
```

Para um teste manual, configure as variáveis somente no processo do backend, autentique-se normalmente, abra **Agendar Novo Encontro → Criar com IA**, descreva o encontro e revise o rascunho. Não coloque a chave em arquivos versionados, no frontend ou na linha de comando compartilhada.

## Limitações

O resultado depende da qualidade da descrição e do catálogo relacional estar sincronizado. Informações ausentes ou ambíguas podem produzir warnings ou `422`; não há escolha aleatória de jogo nem criação automática.
