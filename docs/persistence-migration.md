# Migração gradual da persistência

O domínio de eventos é a primeira fatia cujo PostgreSQL relacional é a fonte
canônica. `eventos` armazena o evento e `evento_participantes` armazena
participantes e fila de espera. Usuários, jogos e partidas continuam disponíveis
no fluxo legado durante a migração.

## Fronteira atual

| Domínio | Fonte atual | Endpoints específicos | Situação |
| --- | --- | --- | --- |
| Eventos | Relacional | Sim | Migrado |
| Participantes | Relacional | Sim | Migrado |
| Fila de espera | Relacional | Sim | Migrado |
| Demais domínios | `app_state`/legado | Não ou parcial | Pendente |

`GET /api/state` mantém o contrato antigo, mas substitui a seção `events` pela
projeção relacional. Um `PUT /api/state` que omite eventos preserva essa seção; um
payload que tenta sobrescrevê-la é recusado com HTTP 409. O shadow sync normal
não grava nem apaga eventos. A importação de eventos legados só ocorre na
inicialização sem estado ou no backfill administrativo explícito.

## Consistência

Criação, edição, inscrição, saída, promoção da fila, cancelamento e conclusão
usam transações. As operações concorrentes bloqueiam a linha do evento antes de
calcular capacidade e posição da fila. Constraints restringem status, tipo de
participação, capacidade positiva e posição única na fila.

A conclusão cria a partida relacional na mesma transação do evento e mantém a
projeção compatível de `sessions` em `app_state`, pois partidas ainda não foram
retiradas por completo do estado legado. Em qualquer falha, a transação é
revertida.

## Próximas etapas

Migrar, uma fatia por vez, partidas, comentários, favoritos e perfis para
endpoints específicos. Depois que consumidores e dados forem validados, o
respectivo trecho poderá ser removido do `PUT /api/state`; `app_state` só deve ser
retirado quando nenhuma seção depender dele.
