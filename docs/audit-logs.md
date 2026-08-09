# Auditoria

`audit_logs` é a única fonte oficial de auditoria. `GET /api/audit-logs` lê essa tabela diretamente e nunca depende de estado agregado ou blob JSON.

As APIs de domínio registram ação, tipo e ID do recurso, resultado e metadados seguros. Atualizações registram somente nomes de campos permitidos; corpos completos, credenciais, conteúdo de perfil e estado global não são gravados.

`GET /api/state` é apenas leitura relacional e não produz evento de auditoria. Não existe `PUT /api/state`.
