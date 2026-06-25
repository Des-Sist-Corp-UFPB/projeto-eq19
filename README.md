# Tabula

Tabula é uma aplicação real para comunidade de jogos de mesa. O projeto permite cadastrar usuários, autenticar login, manter acervo de jogos, marcar eventos, registrar partidas concluídas e acompanhar ranking de vitórias por jogo.

## Produção

A imagem de produção usa um único container com:

- React/Vite compilado para arquivos estáticos;
- Nginx escutando na porta interna `8080`;
- Backend Java/Javalin escutando internamente na porta `8119`;
- Proxy `/api/*` do Nginx para o backend;
- PostgreSQL externo fornecido pelo servidor/professor.

O compose principal mantém o mapeamento exigido:

```yaml
127.0.0.1:8119:8080
```

A aplicação espera as variáveis do servidor:

```env
DB_HOST=postgres
DB_PORT=5432
DB_NAME=eq19
DB_USER=eq19
DB_PASSWORD=kzJrLMkJju9hQkt6MU3f
FRONTEND_URL=https://eq19.dsc.rodrigor.com
BACKEND_PORT=8119
```

## Endpoints principais

- `GET /api/ping` — healthcheck, deve retornar HTTP 200.
- `POST /api/auth/login` — login real no backend.
- `POST /api/auth/register` — cadastro real no backend.
- `GET /api/state` — carrega os dados persistidos no PostgreSQL.
- `PUT /api/state` — salva o estado da aplicação no PostgreSQL, exigindo sessão autenticada após a inicialização.

## Conta administrativa inicial

- E-mail: `admin@tabula.com`
- Senha: `Tabula@2026`

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

## Observações importantes

- O front não salva mais os dados principais da aplicação no `localStorage`.
- Os dados de jogos, eventos, partidas, participantes, ranking e usuários do painel são persistidos no PostgreSQL via backend.
- O navegador guarda apenas o identificador/token de sessão para manter o usuário logado.
- Não versionar `.env` real.
