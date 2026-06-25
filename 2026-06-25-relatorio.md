# Relatório de Avaliação — EQ19 (DSC)

| | |
|---|---|
| **Data** | 2026-06-25 |
| **Repositório** | https://github.com/des-sist-corp-ufpb/projeto-eq19 |
| **Aplicação** | https://eq19.dsc.rodrigor.com |
| **Período de atividade** | 2026-06-10 → 2026-06-10 |
| **Total de commits** (sem merges) | 1 |
| **Integrantes** | Caua Brito Borges (@CauaBt) |

---

## 1. Tecnologias

- Node.js
- Javalin
- Flyway (2 migrations)
- React

---

## 2. Análise Funcional

### Endpoints REST (2 mapeados)

| Método | Path | Arquivo |
|--------|------|---------|
| `POST` | `/auth/register` | `AuthController.java` |
| `GET` | `/ping` | `PingController.java` |

### Entidades / Tabelas (5 encontradas)

- `usuarios (via V1__create_initial_tables.sql)`
- `categorias (via V1__create_initial_tables.sql)`
- `jogos (via V1__create_initial_tables.sql)`
- `anuncios (via V1__create_initial_tables.sql)`
- `codigos_verificacao (via V1__create_initial_tables.sql)`

### Migrations (2 arquivos)

- `V1__create_initial_tables.sql`
- `V1__create_initial_tables.sql`

---

## 3. Análise Arquitetural

| Aspecto | Status | Observação |
|---------|--------|-----------|
| Arquitetura em camadas | ❌ | controller=✅  service=❌  repository=✅ |
| Testes automatizados | ❌ | 0 arquivo(s) de teste |
| Migrations versionadas | ✅ | 2 migration(s) |
| Logging | ✅ | @Slf4j / LoggerFactory / logging.getLogger detectado |
| Autenticação / Segurança | ❌ | não detectado |
| DTOs / Separação de dados | ✅ | classes *DTO / *Request / *Response detectadas |
| Tratamento global de exceções | ❌ | não detectado |
| Documentação de API (OpenAPI) | ❌ | não detectado |
| Variáveis de ambiente | ✅ | .env / @Value / os.environ detectado |
| Dockerfile / docker-compose | ❌ | não encontrado |

---

## 4. Contribuição por Usuário

### Resumo

| Usuário | Commits | % commits | Linhas adicionadas | Linhas no código atual | % código atual |
|---------|---------|-----------|-------------------|----------------------|----------------|
| Caua Brito Borges (@CauaBt) | 1 | 100% | 12.701 | 9.301 | 100% |

### Contribuição por Camada

| Camada | Total linhas | Caua Brito Borges (@CauaBt) |
|--------|-------------|---------|
| Controller | 137 | 100% |
| Frontend | 1.800 | 100% |
| Repository | 46 | 100% |
| Service | 43 | 100% |

---

## 5. Contribuição por Funcionalidade

Baseado em `git blame` nos arquivos de controller e service.

| Arquivo | Total linhas | Caua Brito Borges (@CauaBt) |
|---------|-------------|---------|
| `AuthController.java` | 72 | 100% |
| `api.ts` | 43 | 100% |
| `V1__create_initial_tables.sql` | 39 | 100% |
| `PingController.java` | 26 | 100% |

---

*Relatório gerado automaticamente em 2026-06-25.*
*Os dados de contribuição são baseados em `git log --numstat` (linhas adicionadas) e `git blame` (linhas no código atual), excluindo commits de merge.*