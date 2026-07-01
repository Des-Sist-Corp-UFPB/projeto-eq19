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

* `GET /api/ping` — healthcheck da aplicação, deve retornar HTTP 200.
* `POST /api/auth/login` — login real no backend.
* `POST /api/auth/register` — cadastro real no backend.
* `POST /api/auth/verify-email` — verificação de e-mail por código.
* `POST /api/auth/resend-verification` — reenvio do código de verificação.
* `POST /api/auth/reset-password` — redefinição de senha.
* `POST /api/auth/change-password` — alteração de senha.
* `GET /api/state` — carrega os dados persistidos no PostgreSQL.
* `PUT /api/state` — salva o estado da aplicação no PostgreSQL, exigindo sessão autenticada após a inicialização.

Internamente, o backend registra as rotas sem o prefixo `/api`, pois esse prefixo é tratado pelo Nginx.

## Persistência dos dados

O Tabula persiste os dados no PostgreSQL por meio do backend.

Atualmente, os dados principais da aplicação, como jogos, eventos, partidas, participantes, ranking, comentários e logs de auditoria, são mantidos no estado persistido `app_state.data`, em formato JSONB.

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

A aplicação também possui migrations para tabelas relacionais, como `jogos`, `eventos`, `partidas`, `comentarios`, `favoritos` e `logs`. Essas tabelas foram criadas de forma não destrutiva para evolução da modelagem, mas o fluxo principal atual da aplicação ainda utiliza o estado persistido via `/api/state`.

## Conta administrativa inicial

A aplicação cria uma conta administrativa inicial via migration do banco.

* E-mail: `admin@tabula.com`
* Senha: `Tabula@2026`

Troque essa senha após o primeiro acesso em um ambiente real.

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

## Log de Auditoria

O Tabula possui um módulo de log de auditoria para registrar ações importantes realizadas na aplicação.

As ações auditadas incluem, por exemplo:

* criação de conta;
* alteração de dados de perfil;
* criação, edição e remoção de jogos;
* criação de eventos;
* entrada e saída de participantes em eventos;
* conclusão de eventos;
* registro de partidas concluídas;
* comentários em partidas;
* promoção ou alteração de usuários;
* alterações relevantes no estado da aplicação.

Atualmente, os logs são armazenados junto ao estado persistido da aplicação no PostgreSQL, dentro de `app_state.data`, no array `logs`.

Cada registro de auditoria contém informações como:

```json
{
  "id": "l1",
  "userId": "u1",
  "userName": "Nome do usuário",
  "action": "ação realizada",
  "timestamp": "2026-06-08T10:00:00Z"
}
```

A implementação do log está integrada ao fluxo de atualização do estado da aplicação, principalmente no contexto responsável pelas ações de banco no frontend.

Arquivos relacionados:

* `src/context/DatabaseContext.tsx`
* `src/db/database.ts`
* `src/db/initialData.ts`
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

* Testes executados: 87
* Falhas: 0
* Erros: 0
* Ignorados: 0
* Cobertura por instruções: 91,2%
* Cobertura por linhas: 93,5%
* Cobertura por branches: 74,2%

Relatório HTML:

```text
cobertura/backend/jacoco/index.html
```

Comando utilizado:

```bash
cd backend
mvn clean test jacoco:report
```

### Frontend

Os testes automatizados do frontend foram executados com Vitest e V8 Coverage.

Resultado da última execução:

* Arquivos de teste: 10
* Testes executados: 26
* Falhas: 0
* Cobertura por statements: 86,52%
* Cobertura por linhas: 88,47%
* Cobertura por branches: 71,42%
* Cobertura por funções: 77,62%

Relatório HTML:

```text
cobertura/frontend/index.html
```

Comando utilizado:

```bash
npm run test:coverage
```

## Observações importantes

* O frontend não salva mais os dados principais da aplicação no `localStorage`.
* Os dados de jogos, eventos, partidas, participantes, ranking, comentários, logs e usuários do painel são persistidos no PostgreSQL via backend.
* Atualmente, esses dados são armazenados principalmente dentro de `app_state.data`.
* O navegador guarda apenas o identificador/token de sessão para manter o usuário logado.
* Credenciais reais, como `.env`, senhas de banco e senhas de SMTP, não devem ser versionadas no Git.
* Pastas geradas automaticamente, como `node_modules/`, `coverage/`, `backend/target/` e `dist/`, também não devem ser versionadas.
* Os relatórios finais de cobertura ficam versionados apenas na pasta `cobertura/`.
