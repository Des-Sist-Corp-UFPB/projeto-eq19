# Auditoria oficial

`audit_logs` é a trilha oficial, criada exclusivamente pelo backend. A tabela legada
`logs` e o array `app_state.data.logs` permanecem temporariamente apenas para
compatibilidade e nunca alimentam `GET /audit-logs`.

Essa compatibilidade existe somente no backend para clientes antigos. O frontend
atual descarta `logs` ao sanitizar respostas, não mantém esse campo no estado
tipado e monta o payload de `PUT /state` por lista permitida; portanto, nunca
retransmite nem cria entradas no array legado.

## Privilégios recomendados

Quando o ambiente possuir uma role de migrations separada da role de runtime, a
role da aplicação deve receber somente:

```sql
REVOKE ALL ON audit_logs FROM PUBLIC;
REVOKE ALL ON audit_logs FROM app_runtime;
GRANT SELECT, INSERT ON audit_logs TO app_runtime;
REVOKE ALL ON SEQUENCE audit_logs_id_seq FROM PUBLIC;
GRANT USAGE ON SEQUENCE audit_logs_id_seq TO app_runtime;
```

O nome `app_runtime` é ilustrativo e não deve ser colocado na migration Flyway.
Também é necessário verificar privilégios herdados por associação a outras roles.

O projeto atualmente usa `DB_USER` tanto para executar o Flyway quanto para o pool
da aplicação. Sem credenciais separadas fornecidas pelo ambiente, não é possível
aplicar essa separação com garantia real. Nesse cenário a trigger da V7 mantém a
proteção contra mutações, inclusive contra uma tentativa direta de mudar
`usuario_id` para `NULL`. A exceção da trigger exige profundidade de trigger
interna e existe somente para o `ON DELETE SET NULL` da chave estrangeira.

O teste `AuditLogPostgreSqlPrivilegesTest` cria uma role de runtime separada em um
PostgreSQL descartável e valida `INSERT`/`SELECT`, recusa de `UPDATE`, `DELETE` e
`TRUNCATE`, recusa da alteração manual de `usuario_id` e funcionamento de
`ON DELETE SET NULL` com preservação de `ator_id_externo`. Ele é ignorado
automaticamente quando Docker não está disponível.

## Garantia de gravação

Os eventos que acompanham alteração persistente usam a mesma conexão e transação
da operação principal:

- `LOGIN_SUCCEEDED`;
- `USER_REGISTERED` e o pedido inicial de verificação;
- `EMAIL_VERIFICATION_REQUESTED` em reenvios;
- `EMAIL_VERIFIED` quando aceito;
- `PASSWORD_RESET_COMPLETED` e `PASSWORD_CHANGED`;
- `STATE_UPDATED`.

Se a inserção desses eventos falhar, a operação principal sofre rollback. Envio de
e-mail por SMTP acontece somente depois do commit; nenhuma conexão transacional
permanece aberta durante a chamada externa.

Em `STATE_UPDATED`, a gravação canônica em `app_state` e a auditoria compartilham
a transação. A sincronização relacional já existente continua sendo executada
depois do commit, em modo shadow/best-effort, porque atualmente abre conexões
próprias; falhas nessa projeção não desfazem `app_state` nem criam um segundo
evento oficial.

Eventos de rejeição, que não acompanham uma mutação a confirmar, são best-effort:
`LOGIN_REJECTED`, rejeições de `EMAIL_VERIFIED` e `STATE_UPDATE_REJECTED`. Uma
falha de auditoria nesses casos é registrada no log técnico e não altera o status
HTTP originalmente devido ao cliente.

## Eventos relacionais

As ações `EVENT_CREATED`, `EVENT_UPDATED`, `EVENT_JOINED`, `EVENT_LEFT`,
`EVENT_WAITLISTED`, `EVENT_WAITLIST_PROMOTED`, `EVENT_CANCELLED` e
`EVENT_COMPLETED` são registradas na mesma transação da mutação. Recusas
relevantes usam `EVENT_OPERATION_REJECTED` em gravação best-effort posterior ao
rollback. Os registros contêm somente ator autenticado, ação, recurso, resultado
e motivo genérico; token, senha e payload completo nunca são persistidos.

## Metadados de rede

O backend registra somente o endereço da conexão retornado por Javalin. Cabeçalhos
como `X-Forwarded-For` e `X-Real-IP` não são usados enquanto não houver configuração
explícita de proxies confiáveis. Somente o valor truncado de `User-Agent` é
armazenado; `Authorization`, cookies e demais cabeçalhos nunca são capturados.
