# Tabula

Tabula é uma aplicação real para comunidade de jogos de mesa. O projeto permite cadastrar usuários, autenticar login, manter acervo de jogos, marcar eventos, registrar partidas concluídas e acompanhar ranking de vitórias por jogo.

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

A aplicação espera variáveis de ambiente para configurar banco de dados, URL pública do frontend, porta do backend e envio de e-mails.

Exemplo de configuração:

```env
DB_HOST=postgres
DB_PORT=5432
DB_NAME=eq19
DB_USER=eq19
DB_PASSWORD=********

FRONTEND_URL=https://eq19.dsc.rodrigor.com
BACKEND_PORT=8119

SMTP_HOST=smtp.exemplo.com
SMTP_PORT=587
SMTP_USER=usuario_smtp
SMTP_PASSWORD=senha_smtp
SMTP_FROM=tabula@exemplo.com
SMTP_FROM_NAME=Tabula
```

As credenciais reais não devem ser versionadas no Git.

## Endpoints principais

Externamente, os endpoints são acessados pelo prefixo `/api`:

* `GET /api/ping` — healthcheck do ambiente/professor, dependente do PostgreSQL (retorna HTTP 200 se estiver UP, ou HTTP 503 se estiver DOWN).
* `GET /api/live` — liveness da aplicação, não dependente do PostgreSQL (retorna HTTP 200 se a aplicação estiver rodando).
* `POST /api/auth/login` — login real no backend.
* `POST /api/auth/register` — cadastro real no backend.
* `POST /api/auth/verify-email` — verificação de e-mail por código.
* `POST /api/auth/resend-verification` — reenvio do código de verificação.
* `POST /api/auth/reset-password` — redefinição de senha.
* `POST /api/auth/change-password` — alteração de senha.
* `GET /api/state` — carrega os dados persistidos no PostgreSQL.
* `PUT /api/state` — salva o estado da aplicação no PostgreSQL, exigindo sessão autenticada após a inicialização.
* `GET /api/events` e `GET /api/events/{id}` — consultam eventos relacionais.
* `POST /api/events` e `PATCH /api/events/{id}` — criam e editam eventos.
* `POST /api/events/{id}/join` e `POST /api/events/{id}/leave` — gerenciam a própria participação e fila.
* `POST /api/events/{id}/cancel` e `POST /api/events/{id}/complete` — encerram eventos do organizador.

Internamente, o backend registra as rotas sem o prefixo `/api`, pois esse prefixo é tratado pelo Nginx.

## Assistente de eventos com IA

O fluxo **Criar com IA** gera rascunhos editáveis de eventos a partir de linguagem natural e permite refiná-los antes da confirmação. A integração acontece somente no backend, por LiteLLM com `gpt-4o-mini`, nos endpoints `POST /api/ai/event-drafts` e `POST /api/ai/event-drafts/refine`.

Gerações e refinamentos são auditados, mas nunca salvam o evento automaticamente: a revisão e a confirmação humana continuam obrigatórias. Consulte [docs/ai-event-assistant.md](docs/ai-event-assistant.md).

## Persistência dos dados

O Tabula persiste os dados no PostgreSQL por meio do backend.

Eventos, participantes e fila de espera usam as tabelas relacionais como fonte
canônica. Os demais domínios ainda mantidos no fluxo agregado usam
`app_state.data`; a auditoria oficial usa exclusivamente `audit_logs`.

Tabela principal usada pelo estado da aplicação:

```text
app_state
```

Campos principais:

```text
id
data
updated_at
```

A aplicação também possui migrations para tabelas relacionais, como `jogos`,
`eventos`, `evento_participantes`, `partidas`, `comentarios`, `favoritos` e
`audit_logs`. A migração é incremental: eventos já usam endpoints específicos,
enquanto as fatias restantes preservam compatibilidade via `/api/state`. Veja
[docs/persistence-migration.md](docs/persistence-migration.md).

| Domínio | Fonte atual | Endpoints específicos | Situação |
| --- | --- | --- | --- |
| Eventos | Relacional | Sim | Migrado |
| Participantes | Relacional | Sim | Migrado |
| Fila de espera | Relacional | Sim | Migrado |
| Demais domínios | `app_state`/legado | Não ou parcial | Pendente |

## Migração Relacional

Este projeto utiliza uma estratégia de migração segura, incremental e tolerante a falhas para mover os dados da aplicação para um modelo relacional no PostgreSQL.

### 1. Arquitetura da Migração Atual

Para os domínios ainda não migrados, a aplicação utiliza `app_state` (coluna
`data` do tipo JSONB) como fonte de dados e fallback. Eventos, participantes e
fila já são canônicos no relacional e não fazem rollback para o JSON legado.

O fluxo de escrita ocorre da seguinte forma:
1. **Salvamento Principal**: O endpoint `PUT /state` recebe o JSON do frontend e o salva diretamente no banco de dados na tabela `app_state.data`.
2. **Sincronização em Sombra (Shadow Sync)**: Após o salvamento com sucesso, o backend inicia uma sincronização em segundo plano (shadow mode) utilizando o `RelationalStateSyncService`.
3. Sincroniza as fatias legadas mapeadas. Eventos e participantes são
explicitamente ignorados nesse shadow sync e só são importados pelo bootstrap ou
backfill administrativo.

Pontos importantes:
- A tabela `app_state.data` **não foi removida** e continua sendo atualizada.
- Se a sincronização relacional falhar por qualquer motivo (erros de chave, constraint ou banco), o erro é registrado no log do servidor, mas a resposta de sucesso (`200 OK`) é retornada normalmente ao cliente.
- A rota `/ping` e `/api/ping` dependem do PostgreSQL, retornando status unhealthy se o banco estiver indisponível (garantindo que o status do projeto fique vermelho para o monitoramento do professor), enquanto as rotas `/live` e `/api/live` permanecem independentes do banco.

### 2. Arquitetura de Leitura

O fluxo de leitura (`GET /state`) possui três modos operacionais controlados por flags de recurso:

1. **Leitura Padrão (Legada)**: Quando a leitura relacional está desativada, a aplicação lê diretamente a coluna `app_state.data`.
2. **Leitura Relacional Sem Guardão**: Quando `RELATIONAL_STATE_READ_ENABLED=true` e o guardão está desativado, o backend tenta reconstruir o JSON a partir das tabelas relacionais usando `RelationalStateReadService`. Se houver falha, ele reverte automaticamente para o `app_state.data`.
3. **Leitura Relacional Protegida (Guarded Relational Read)**: Quando `RELATIONAL_STATE_READ_ENABLED=true` e `RELATIONAL_STATE_READ_GUARD_ENABLED=true`, a rota executa uma verificação ativa de consistência em tempo de execução:
   - Lê o `app_state.data` legado e o JSON reconstruído relacional.
   - Compara ambos através do `RelationalStateComparisonService`.
   - Se a comparação for bem-sucedida (`comparison.ok = true`), retorna o JSON relacional.
   - Se a comparação falhar (`comparison.ok = false`) ou se houver qualquer erro/exceção, o sistema registra um aviso no log do servidor e retorna o `app_state.data` com status `200 OK`, sem afetar o usuário final.

### 3. Serviços da Migração Relacional

- **RelationalStateSyncService**: Responsável pela sincronização e inserção idempotente das tabelas relacionais a partir do JSONB. Utilizado no shadow sync (`PUT /state`) e no endpoint administrativo de backfill.
- **RelationalStateReadService**: Reconstrói e formata o payload JSON compatível com o tipo `DatabaseState` do frontend a partir das tabelas relacionais do banco.
- **RelationalStateComparisonService**: Compara estruturalmente os dois payloads JSON e gera um relatório. Ele detecta arrays de primeiro nível, discrepâncias de contagem de itens, IDs ausentes (erros críticos) e IDs extras ou diferenças em campos principais como `name`, `email`, `role`, `category`, `gameId`, `organizerId`, `winnerId`, `status` e `action` (warnings). Ignora safe defaults como avatares, imagens de capa, descrições, formatações de timestamp e campos opcionais vazios.

### 4. Endpoints de Diagnóstico e Administração

O backend expõe dois endpoints estritamente administrativos e protegidos por flags de recursos:

- **GET /state/relational-comparison** (Flag: `RELATIONAL_STATE_COMPARISON_ENABLED=true`):
  Retorna um relatório estruturado comparando as duas fontes de dados em tempo real. Não altera nenhum dado (operação somente leitura). Se a flag estiver desativada ou ausente, retorna `404 Not Found`.
- **POST /state/relational-backfill** (Flag: `RELATIONAL_STATE_BACKFILL_ENABLED=true`):
  Lê o `app_state.data` e realiza a carga inicial (sincronização) relacional de forma manual e idempotente. Retorna um relatório de comparação pós-sincronização. Deve ser desativado após o uso. Se a flag estiver desativada ou ausente, retorna `404 Not Found`.

### 5. Variáveis de Ambiente e Feature Flags

- `RELATIONAL_STATE_READ_ENABLED=true`: Ativa a tentativa de leitura relacional no `GET /state`.
- `RELATIONAL_STATE_READ_GUARD_ENABLED=true`: Ativa a verificação ativa de integridade e comparação no `GET /state` antes de servir o JSON relacional.
- `RELATIONAL_STATE_COMPARISON_ENABLED=true`: Habilita o endpoint de diagnóstico `/state/relational-comparison`.
- `RELATIONAL_STATE_BACKFILL_ENABLED=true`: Habilita o endpoint administrativo `/state/relational-backfill`.

### 6. Roteiro Recomendado de Rollout Seguro

Siga este procedimento para ativar a leitura relacional em produção sem downtime ou riscos:

1. **Passo 1 — Modo Seguro Padrão**: Mantenha as variáveis de leitura desativadas. A escrita shadow sync já popula gradualmente as tabelas relacionais a cada ação do usuário.
2. **Passo 2 — Executar Carga Inicial (Backfill)**:
   - Configure temporariamente `RELATIONAL_STATE_BACKFILL_ENABLED=true` no ambiente.
   - Faça uma chamada `POST /api/state/relational-backfill`.
   - Certifique-se de que a resposta retorne sucesso e remova a flag `RELATIONAL_STATE_BACKFILL_ENABLED`.
3. **Passo 3 — Executar Validação Cruzada**:
   - Configure temporariamente `RELATIONAL_STATE_COMPARISON_ENABLED=true`.
   - Faça uma chamada `GET /api/state/relational-comparison`.
   - Confirme se a comparação retornou `"ok": true`. Remova a flag `RELATIONAL_STATE_COMPARISON_ENABLED`.
4. **Passo 4 — Habilitar Leitura Protegida (Guarded Read)**:
   - Configure no ambiente:
     ```env
     RELATIONAL_STATE_READ_ENABLED=true
     RELATIONAL_STATE_READ_GUARD_ENABLED=true
     ```
   - Reinicie a aplicação. O sistema agora lê do relacional de forma protegida. Mismatches reverterão automaticamente de forma silenciosa para o `app_state`.
5. **Passo 5 — Ativação Direta Opcional**:
   - Após constatar estabilidade e logs livres de avisos, você pode opcionalmente desativar a leitura protegida mantendo apenas `RELATIONAL_STATE_READ_ENABLED=true` e definindo `RELATIONAL_STATE_READ_GUARD_ENABLED=false`.
   - **Nota**: A tabela `app_state` e o shadow sync continuam ativos em produção como fallback essencial.

### 7. Instruções de Rollback Imediato

Se houver qualquer instabilidade, indisponibilidade ou inconsistência de dados com a leitura relacional, remova ou defina as seguintes flags de leitura como `false`:

```env
RELATIONAL_STATE_READ_ENABLED=false
RELATIONAL_STATE_READ_GUARD_ENABLED=false
RELATIONAL_STATE_COMPARISON_ENABLED=false
RELATIONAL_STATE_BACKFILL_ENABLED=false
```

As fatias ainda legadas voltarão a usar `app_state.data`. Essa configuração não
reverte eventos, participantes ou fila, que permanecem relacionais.

### 8. Explicação para Avaliação / Professor

Esta migração seguiu uma estratégia incremental recomendada para sistemas em produção:
- **Evolução Não Destrutiva**: Preserva o `app_state.data` original como fonte primária e backup.
- **Sincronização em Sombra**: A escrita relacional funciona de forma passiva sem afetar o tempo de resposta ou disponibilidade do cliente.
- **Auditoria ao Vivo**: Os endpoints de comparação e backfill permitem verificar a consistência da migração antes de ligar a chave.
- **Rede de Segurança Ativa (Guarded Read)**: Se houver qualquer divergência em produção, a aplicação reverte de forma transparente e serve o estado JSONB original.
- **Rollback Instantâneo**: Desfazer a mudança requer apenas alterar variáveis de ambiente, sem necessidade de reverter migrations ou dados.

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

O backend também aplica autorização granular antes de persistir alterações no
`app_state`. A identidade do usuário é obtida exclusivamente pelo token validado,
sem confiar em `userId`, `organizerId` ou `authorId` enviados pelo frontend.

Entre as regras aplicadas:

- usuários alteram somente o próprio perfil e favoritos;
- organizadores gerenciam apenas os próprios eventos;
- participantes entram ou saem de eventos somente em nome próprio;
- comentários são alterados apenas pelo respectivo autor;
- usuários comuns não podem alterar papéis;
- logs oficiais não podem ser modificados pelo `PUT /api/state`;
- operações sem permissão retornam HTTP 403 e são auditadas.

A autorização é executada antes de qualquer gravação no `app_state`.

## Log de Auditoria

O Tabula mantém a trilha oficial na tabela relacional append-only `audit_logs`.
Somente o backend cria esses eventos. Enquanto as alterações de domínio ainda
chegam agregadas pelo `PUT /api/state`, elas são registradas como `STATE_UPDATED`
com `changedSections`.

A tabela `logs` e um eventual array `app_state.data.logs` são preservados apenas
para compatibilidade temporária com clientes antigos. O frontend atual descarta
esse campo ao carregar o estado, nunca o inclui no `PUT /api/state` e não cria
novos registros legados. A página administrativa consulta exclusivamente
`GET /api/audit-logs`.

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

Resultado da última execução:

- Testes executados: 196
- Testes aprovados: 196
- Falhas: 0
- Erros: 0
- Ignorados: 0
- Cobertura por instruções: 86,60% (13.520/15.612)
- Cobertura por branches: 70,06% (1.006/1.436)
- Pacote `br.com.tabula.ai`: 90,65% de instruções e 71,95% de branches

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

Resultado da última execução:

* Arquivos de teste: 14
* Testes aprovados: 46
* Falhas: 0
* Cobertura por statements: 87,01% (1.199/1.378)
* Cobertura por linhas: 89,02% (1.119/1.257)
* Cobertura por branches: 76,96% (508/660)
* Cobertura por funções: 76,98% (291/378)

Os testes de interface cobrem também a auditoria, os fluxos manuais de eventos e os fluxos de geração e refinamento por IA, inclusive refinamentos sucessivos, erros do provedor e preservação do formulário.

Os builds de cobertura exigem no mínimo 85% de statements e 85% de linhas no frontend. O JaCoCo exige no mínimo 85% de instruções no backend.

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
* Os dados de jogos, eventos, partidas, participantes, ranking, comentários, logs e usuários do painel são persistidos no PostgreSQL via backend.
* Atualmente, esses dados são armazenados principalmente dentro de `app_state.data`.
* O navegador guarda apenas o identificador/token de sessão para manter o usuário logado.
* Credenciais reais, como `.env`, senhas de banco e senhas de SMTP, não devem ser versionadas no Git.
* Pastas geradas automaticamente, como `node_modules/`, `coverage/`, `backend/target/` e `dist/`, também não devem ser versionadas.
* Os relatórios finais de cobertura ficam versionados apenas na pasta `cobertura/`.
