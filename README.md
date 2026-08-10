# Tabula

Tabula é uma aplicação real para comunidade de jogos de mesa. O projeto permite cadastrar usuários, autenticar login, manter acervo de jogos, marcar eventos, registrar partidas concluídas e acompanhar ranking de vitórias por jogo.

## Vídeo

Link para o vídeo: https://youtu.be/PN_BBGWrKBs

## Produção

A imagem de produção usa um único container com:

* React/Vite compilado para arquivos estáticos;
* Nginx escutando na porta interna `8080`;
* Backend Java/Javalin escutando internamente na porta `8119`;
* Proxy `/api/*` do Nginx para o backend;
* PostgreSQL fornecido pelo ambiente do servidor/professor.

O compose principal mantém o mapeamento exigido:

```yaml
127.0.0.1:8119:8080
```

No ambiente de produção, o fluxo é:

```text
Navegador/host -> porta 8119 -> Nginx interno na porta 8080 -> Backend Javalin na porta 8119
```

O frontend consome a API usando o prefixo `/api`. O Nginx remove esse prefixo ao encaminhar para o backend.

Exemplo:

```text
GET /api/ping
```

é encaminhado internamente para:

```text
GET /ping
```

## Variáveis de ambiente

A aplicação espera variáveis de ambiente para configurar banco de dados, URLs do sistema, e-mail SMTP, assistente de IA e observabilidade via OpenTelemetry.

Exemplo de configuração (`.env`):

```env
# Banco de dados (PostgreSQL relacional)
DB_HOST=postgres
DB_PORT=5432
DB_NAME=eq19
DB_USER=eq19
DB_PASSWORD=********

# Servidor e URLs
BACKEND_PORT=8119
FRONTEND_URL=https://eq19.dsc.rodrigor.com
BACKEND_URL=https://eq19.dsc.rodrigor.com
VITE_API_BASE_URL=/api

# Envio de e-mail (SMTP transacional)
SMTP_HOST=smtp.exemplo.com
SMTP_PORT=587
SMTP_USER=usuario_smtp
SMTP_PASSWORD=senha_smtp
SMTP_FROM=tabula@exemplo.com
SMTP_FROM_NAME=Tabula

# Assistente de eventos com IA (LiteLLM / somente backend)
LITELLM_API_KEY=<CHAVE_LITELLM_DA_EQUIPE>
LITELLM_BASE_URL=https://llm.rodrigor.com
LITELLM_MODEL=gpt-4o-mini
AI_REQUEST_TIMEOUT_SECONDS=15
AI_MAX_COMPLETION_TOKENS=300
AI_MAX_CANDIDATE_GAMES=15
AI_MAX_REQUESTS_PER_USER_HOUR=3
AI_MAX_REQUESTS_PER_DAY=10
AI_RETRY_ENABLED=false
APP_TIME_ZONE=America/Sao_Paulo

# Observabilidade OpenTelemetry
JAVA_TOOL_OPTIONS=-javaagent:/app/opentelemetry-javaagent.jar
OTEL_SERVICE_NAME=dsc-eq19
OTEL_EXPORTER_OTLP_ENDPOINT=https://otel.dsc.rodrigor.com
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_EXPORTER_OTLP_HEADERS=Authorization=Bearer ********
```

As credenciais e tokens reais não devem ser versionadas no Git.

## Endpoints principais

Externamente, os endpoints são acessados pelo prefixo `/api`:

* `GET /api/ping` — healthcheck do ambiente/professor, dependente do PostgreSQL (retorna HTTP 200 se UP, HTTP 503 se DOWN).
* `GET /api/live` — liveness da aplicação, independente do banco de dados (retorna HTTP 200 se a aplicação estiver rodando).
* `POST /api/auth/login` e `POST /api/auth/register` — autenticação e cadastro de usuários no backend.
* `POST /api/auth/verify-email` e `POST /api/auth/resend-verification` — verificação de e-mail por código.
* `POST /api/auth/reset-password` e `POST /api/auth/change-password` — gestão de senhas.
* `GET /api/state` — projeção 100% relacional para compatibilidade com o cache frontend (`DatabaseState`).
* `GET /api/games`, `POST /api/games`, `PUT /api/games/{id}`, `DELETE /api/games/{id}` — catálogo de jogos.
* `GET /api/events`, `POST /api/events`, `PATCH /api/events/{id}` — consulta, criação e edição de eventos.
* `POST /api/events/{id}/join`, `POST /api/events/{id}/leave` — participação e fila de espera em eventos.
* `POST /api/events/{id}/cancel`, `POST /api/events/{id}/complete` — cancelamento e encerramento de eventos.
* `GET /api/sessions`, `POST /api/sessions`, `DELETE /api/sessions/{id}` — registro e gestão de partidas.
* `GET /api/sessions/{id}/comments`, `POST /api/sessions/{id}/comments`, `DELETE /api/sessions/{id}/comments/{id}` — comentários.
* `GET /api/favorites`, `POST /api/favorites/{gameId}`, `DELETE /api/favorites/{gameId}` — jogos favoritos do usuário.
* `GET /api/profile`, `PUT /api/profile` — consulta e atualização de perfil do usuário.
* `GET /api/audit-logs` — consulta da trilha append-only de auditoria oficial (somente admins).
* `PATCH /api/users/{id}/role`, `DELETE /api/users/{id}` — administração de papéis e exclusão de contas (somente admins).
* `POST /api/ai/event-drafts`, `POST /api/ai/event-drafts/refine` — geração e refinamento de rascunhos de eventos por IA.

Internamente, o backend registra as rotas sem o prefixo `/api`, pois esse prefixo é tratado pelo Nginx.

## Assistente de eventos com IA

O fluxo **Criar com IA** gera rascunhos editáveis de eventos a partir de linguagem natural e permite refiná-los antes da confirmação. A integração acontece somente no backend, por LiteLLM com `gpt-4o-mini`, nos endpoints `POST /api/ai/event-drafts` e `POST /api/ai/event-drafts/refine`.

Gerações e refinamentos são auditados, mas nunca salvam o evento automaticamente: a revisão e a confirmação humana continuam obrigatórias. Consulte [docs/ai-event-assistant.md](docs/ai-event-assistant.md).

## Persistência dos dados

O Tabula utiliza PostgreSQL relacional como fonte de verdade dos dados da aplicação.

A migração incremental foi concluída na V15:

- a V14 removeu a tabela legada `app_state`;
- a V15 removeu a tabela legada `logs`;
- usuários, autenticação, perfis, jogos, favoritos, eventos, partidas, comentários e auditoria são persistidos em tabelas relacionais;
- `GET /api/state` permanece apenas como uma projeção agregada de compatibilidade para o frontend;
- não existe mais `PUT /api/state`, shadow sync, fallback JSON, backfill ou comparação entre estados;
- a auditoria oficial utiliza exclusivamente a tabela `audit_logs`.

O `DatabaseState` utilizado pelo frontend funciona apenas como DTO agregado e cache em memória. As alterações são persistidas por meio das APIs específicas de cada domínio.

Mais detalhes sobre a arquitetura e o histórico da migração estão em
[docs/persistence-migration.md](docs/persistence-migration.md).

## Conta administrativa inicial

A conta administrativa inicial é criada pela migration. As credenciais devem ser
obtidas por meio da configuração segura do ambiente e alteradas no primeiro acesso.

## Teste local opcional

Para testar localmente com PostgreSQL em container:

```bash
docker compose -f docker/docker-compose.local.yml up --build
```

Acesse:

```text
http://localhost:8119
```

No ambiente local, o compose usa um container PostgreSQL próprio e expõe a aplicação em:

```text
127.0.0.1:8119
```

## Observabilidade Local (OpenTelemetry)

Para rodar a aplicação local com coleta de traces via OpenTelemetry, Grafana e Tempo, consulte o guia detalhado:
- [Guia de Instrumentação OpenTelemetry Local](docs/opentelemetry-local.md)

## Autenticação e autorização

A autenticação é realizada no backend por meio de tokens Bearer persistidos no
PostgreSQL. Tokens ausentes, inválidos ou expirados retornam HTTP 401.

O backend também aplica autorização granular antes de persistir alterações nas
tabelas relacionais. A identidade do usuário é obtida exclusivamente pelo token validado,
sem confiar em `userId`, `organizerId` ou `authorId` enviados pelo frontend.

Entre as regras aplicadas:

- usuários alteram somente o próprio perfil e favoritos;
- organizadores gerenciam apenas os próprios eventos;
- participantes entram ou saem de eventos somente em nome próprio;
- comentários são alterados apenas pelo respectivo autor;
- usuários comuns não podem alterar papéis;
- logs de auditoria oficiais só podem ser criados pelo backend;
- operações sem permissão retornam HTTP 403 e são auditadas.

A autorização é executada antes de qualquer gravação de domínio.

## Log de Auditoria

O Tabula mantém a trilha oficial na tabela relacional append-only `audit_logs`.
Somente o backend cria esses eventos e a página administrativa consulta
exclusivamente `GET /api/audit-logs`.

Arquivos relacionados:

* `docs/audit-logs.md`
* `src/pages/AuditLogs.tsx`
* `backend/src/main/java/br/com/tabula/service/AuditLogService.java`
* `backend/src/main/java/br/com/tabula/controller/StateController.java`

## Integração com Serviço Externo

O Tabula possui integração com um serviço externo de envio de e-mails via SMTP.

Essa integração é utilizada no fluxo de autenticação, principalmente para envio do código de verificação de e-mail durante o cadastro de novos usuários.

Quando um usuário cria uma conta, o backend gera um código de verificação e utiliza o serviço de e-mail para enviar esse código ao endereço informado.

Serviço externo utilizado:

* Servidor SMTP / provedor de e-mail transacional;
* Pode ser configurado com provedores como Resend, Gmail SMTP ou outro serviço compatível com SMTP.

Finalidade da integração:

* envio de código de verificação de e-mail;
* suporte ao fluxo real de cadastro e ativação de conta.

Arquivos relacionados:

* `backend/src/main/java/br/com/tabula/service/EmailService.java`
* `backend/src/main/java/br/com/tabula/controller/AuthController.java`

Variáveis de ambiente utilizadas:

```env
SMTP_HOST=
SMTP_PORT=
SMTP_USER=
SMTP_PASSWORD=
SMTP_FROM=
SMTP_FROM_NAME=
```

As credenciais reais não devem ser versionadas no Git. O arquivo `.env` real deve existir apenas no ambiente de execução.

## Cobertura de Testes

O projeto possui testes automatizados para backend e frontend, com relatórios de cobertura versionados na pasta `cobertura/`.

### Backend

Os testes automatizados do backend foram executados com Maven e JaCoCo.

Resultado da execução atual:

- Testes executados: 163
- Testes aprovados: 163
- Falhas: 0
- Erros: 0
- Ignorados: 0
- Instruções: 88%
- Linhas: 89,58%
- Branches: 67%
- Methods: 93,03%
- Classes: 97,37%
- Classes analisadas: 114
- Resultado: BUILD SUCCESS
- JaCoCo confirmou: "All coverage checks have been met."

Os testes incluem cenários específicos da trilha de auditoria e da integração com IA, como autenticação, limites de consumo, retry e telemetria de tokens. Os testes da IA usam clientes simulados e servidores HTTP locais; não consomem a cota LiteLLM.

Relatórios:

```text
cobertura/backend/jacoco/index.html
cobertura/backend/jacoco/jacoco.xml
```

Comando utilizado:

```bash
cd backend
mvn clean test jacoco:report
```

### Frontend

Os testes automatizados do frontend foram executados com Vitest e V8 Coverage.

Resultado da execução atual:

- Arquivos de teste: 15
- Arquivos aprovados: 15
- Testes: 67
- Testes aprovados: 67
- Falhas: 0
- Statements: 86,37%
- Branches: 77,98%
- Functions: 77,38%
- Lines: 88,39%
- Threshold global de statements: 85%
- Resultado: cobertura aprovada

Os testes de interface cobrem também a auditoria, os fluxos manuais de eventos e os fluxos de geração e refinamento por IA, inclusive refinamentos sucessivos, erros do provedor e preservação do formulário.

Relatórios:

```text
cobertura/frontend/index.html
cobertura/frontend/coverage-summary.json
```

Comando utilizado:

```bash
npm run test:coverage
```

Detalhes, limitações e links para os relatórios estão em [docs/testing-and-coverage.md](docs/testing-and-coverage.md).

## Observações importantes

* O frontend não salva mais os dados principais da aplicação no `localStorage`.
* Os dados de jogos, eventos, partidas, participantes, ranking, comentários, logs de auditoria e usuários do painel são persistidos no PostgreSQL via backend.
* Esses dados são armazenados nas tabelas relacionais do PostgreSQL.
* O navegador guarda apenas o identificador/token de sessão para manter o usuário logado.
* Credenciais reais, como `.env`, senhas de banco e senhas de SMTP, não devem ser versionadas no Git.
* Pastas geradas automaticamente, como `node_modules/`, `coverage/`, `backend/target/` e `dist/`, também não devem ser versionadas.
* Os relatórios finais de cobertura ficam versionados apenas na pasta `cobertura/`.
### Persistência de partidas

Partidas e seus participantes usam PostgreSQL relacional como fonte
autoritativa, com endpoints `/api/sessions`. A conclusão de um evento cria a
partida na mesma transação e a unicidade do vínculo com o evento é protegida
no banco. O `GET /api/state` mantém uma projeção relacional de compatibilidade
para o cache do frontend.
