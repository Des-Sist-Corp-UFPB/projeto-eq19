# Assistente de rascunhos de eventos

## Objetivo e fluxo

O assistente transforma uma descrição em linguagem natural em um rascunho editável e permite refiná-lo. A geração inicial e cada refinamento correspondem, cada um, a uma nova chamada à LLM. O usuário revisa os campos e somente o botão normal **Agendar Encontro** salva o evento.

```text
Events.tsx → POST /api/ai/event-drafts → limite de uso
           → pré-filtro local do catálogo → LiteLLM (gpt-4o-mini)
           ← rascunho validado no backend ← JSON do modelo

Events.tsx → POST /api/ai/event-drafts/refine → o mesmo limite de uso
           → rascunho atual + instrução → LiteLLM → rascunho refinado
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

O limite local responde `429` com o código `AI_USAGE_LIMIT_REACHED`. Também são esperados `400` para prompt inválido, `401` para sessão inválida, `422` para saída não validável, `429` para limite do provedor, `502` para resposta externa inválida e `503` para configuração ausente ou indisponibilidade transitória.

## Configuração econômica

Somente o backend lê:

```env
LITELLM_API_KEY=<CHAVE_LITELLM_DA_EQUIPE>
LITELLM_BASE_URL=https://llm.rodrigor.com
LITELLM_MODEL=gpt-4o-mini
AI_REQUEST_TIMEOUT_SECONDS=15
AI_MAX_COMPLETION_TOKENS=300
AI_RETRY_ENABLED=false
AI_MAX_CANDIDATE_GAMES=15
AI_MAX_REQUESTS_PER_USER_HOUR=3
AI_MAX_REQUESTS_PER_DAY=10
APP_TIME_ZONE=America/Sao_Paulo
```

Os padrões priorizam o orçamento total de US$ 2: `gpt-4o-mini`, temperatura `0.1`, no máximo 300 tokens de conclusão e retry desativado. Se `AI_RETRY_ENABLED=true`, ocorre no máximo uma nova tentativa, apenas para timeout, conexão, `429` ou `5xx`; nunca para `400`, `401` ou `403`.

Os limites são mantidos em memória de forma thread-safe: três chamadas por usuário por hora e dez globais por dia. Geração e refinamentos usam a mesma instância do limitador e compartilham ambas as cotas. A identidade vem exclusivamente do Bearer token validado. Uma rejeição pelo limite acontece antes da chamada à LiteLLM. Os contadores reiniciam com o backend e não são adequados para múltiplas instâncias sem armazenamento compartilhado.

## Redução de tokens e validação

Antes da chamada externa, Java pontua o catálogo por nome, categoria, quantidade de jogadores, duração e complexidade. Somente os 15 candidatos mais relevantes são enviados, em ordem determinística, com os campos compactos `id`, `nome`, `categoria`, `minPlayers`, `maxPlayers`, `duracao` e `complexidade`. O fallback não é aleatório.

O prompt deve ter de 5 a 1000 caracteres. A saída precisa conter um objeto JSON; o backend verifica o jogo contra o banco, data e fuso, horário, limites de participantes, localização, descrição e warnings. O rascunho continua editável e não é salvo automaticamente.

## Auditoria e logs

Na geração e no refinamento, quando a LiteLLM devolve `usage`, o backend extrai `prompt_tokens`, `completion_tokens` e `total_tokens`. A telemetria também registra quantas chamadas ao provedor ocorreram, incluindo retry. A ausência de `usage` não quebra a operação. Prompt, instrução, rascunho, resposta bruta, chave e cabeçalho de autorização nunca são auditados ou logados.

## Refinamento e confirmação humana

Depois da geração, o frontend envia a instrução e os valores atuais do formulário para `POST /ai/event-drafts/refine`. Edições manuais também entram no rascunho atual. Em caso de `429` ou outra falha, todos os campos e a instrução permanecem intactos. Cada refinamento consome uma nova chamada e participa do mesmo limite da geração inicial.

Exemplo: gere “Crie uma mesa de Xadrez sábado às 18h”; refine com “Troque para domingo às 15h”; revise e confirme manualmente.

## Testes e operação

Os testes usam clientes falsos e servidores HTTP locais, sem consumir a LiteLLM:

```bash
cd backend
mvn test
cd ..
npm test
npm run lint
npm run build
```

Para um teste manual, configure as variáveis no processo do backend, autentique-se, abra **Agendar Novo Encontro**, descreva o encontro e clique uma vez em **Preencher formulário com IA**. Ajuste os campos manualmente antes de salvar. Não coloque a chave no frontend, em arquivos versionados ou em uma linha de comando compartilhada.

## Limitações

A seleção local é heurística e o resultado depende da descrição e do catálogo relacional. Informações ausentes ou ambíguas podem gerar warnings ou `422`. Não há escolha aleatória, criação automática, MCP, moderação ou detecção de anomalias nesta etapa.
