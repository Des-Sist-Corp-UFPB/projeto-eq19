# Persistência relacional

A migração incremental foi concluída na V15. PostgreSQL relacional é a fonte de verdade de usuários, autenticação, perfis, jogos, favoritos, eventos, partidas, comentários e auditoria.

`DatabaseState` permanece somente como DTO agregado e cache em memória do frontend. Alterar esse objeto não persiste dados. Toda mutação usa a API específica do domínio e atualiza o cache apenas depois de uma resposta bem-sucedida.

`GET /api/state` é uma projeção de compatibilidade construída integralmente pelas tabelas relacionais. Não existe `PUT /api/state`, shadow sync, fallback JSON ou endpoint de backfill/comparação.

A V14 remove `app_state` sem `CASCADE`. As referências ao blob que permanecem nas migrations V2 e V10–V13 são parte imutável do histórico Flyway: em um banco novo elas criam e usam temporariamente a tabela antes da remoção final.

A V15 remove, também sem `CASCADE`, a tabela `logs` que alimentava apenas uma
projeção antiga. A auditoria oficial permanece em `audit_logs`.

## APIs authoritative

- autenticação: `/auth/*`;
- perfil: `GET/PUT /profile`;
- usuários administrativos: `PATCH /users/{id}/role`, `DELETE /users/{id}`;
- catálogo: `/games`;
- favoritos: `/favorites`;
- eventos: `/events`;
- partidas: `/sessions`;
- comentários: `/sessions/{id}/comments`;
- auditoria: `/audit-logs`.

Vitórias, ranking, taxa de vitória, popularidade e demais métricas são derivadas das relações existentes e não têm cópia persistida no DTO agregado.

O corpo de criação de comentário contém somente `content`; a autoria vem do token
autenticado. O mapeador JSON do Javalin ignora propriedades desconhecidas, então
clientes antigos podem continuar enviando campos extras, mas eles não selecionam
nem alteram a identidade do autor.
