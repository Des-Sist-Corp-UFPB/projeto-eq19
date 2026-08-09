# Persistência relacional

A migração incremental foi concluída na V15. O PostgreSQL relacional é a fonte de verdade de usuários, autenticação, perfis, jogos, favoritos, eventos, partidas, comentários e auditoria.

`DatabaseState` permanece somente como DTO agregado e cache em memória do frontend. Alterar esse objeto não persiste dados. Toda mutação usa a API específica do domínio e atualiza o cache apenas depois de uma resposta bem-sucedida.

`GET /api/state` é uma projeção de compatibilidade construída integralmente a partir das tabelas relacionais. Não existe `PUT /api/state`, sincronização em sombra (shadow sync), fallback para JSON ou endpoint de backfill/comparação.

A V14 removeu `app_state` sem `CASCADE`. As referências ao blob que permanecem nas migrations V2 e V10–V13 fazem parte do histórico imutável do Flyway: em um banco novo, elas criam e utilizam temporariamente a tabela antes da remoção final.

A V15 também removeu, sem `CASCADE`, a tabela `logs`, que alimentava apenas uma projeção antiga. A auditoria oficial permanece em `audit_logs`.

## APIs por domínio

- autenticação: `/auth/*`;
- perfil: `GET/PUT /profile`;
- usuários administrativos: `PATCH /users/{id}/role`, `DELETE /users/{id}`;
- catálogo: `/games`;
- favoritos: `/favorites`;
- eventos: `/events`;
- partidas: `/sessions`;
- comentários: `/sessions/{id}/comments`;
- auditoria: `/audit-logs`.

Vitórias, ranking, taxa de vitória, popularidade e demais métricas são derivadas das relações existentes e não possuem cópia persistida no DTO agregado.

O corpo da criação de comentários contém somente `content`; a autoria é obtida a partir do token autenticado. O mapeador JSON do Javalin ignora propriedades desconhecidas, portanto clientes antigos podem continuar enviando campos extras, mas esses campos não selecionam nem alteram a identidade do autor.