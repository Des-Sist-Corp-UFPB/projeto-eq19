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
## Etapa 2 — partidas e participantes

Partidas (`sessions` na API, `partidas` no PostgreSQL) e seus participantes
passaram a ter fonte autoritativa relacional. A API própria expõe `GET
/sessions`, `GET /sessions/{id}`, `POST /sessions` e `DELETE /sessions/{id}`.

A conclusão de evento bloqueia o evento, cria uma única partida e seus
participantes confirmados, altera o evento e grava auditoria na mesma
transação. O índice único parcial de `partidas.evento_id` impede duplicidade
mesmo entre requisições concorrentes. Não existe mais escrita em
`app_state.sessions` nesse fluxo.

O `GET /state` ainda projeta as partidas relacionais para Stats, PlayerProfile,
Games, Home, busca global e detalhes legados. O `PUT /state` rejeita com 409
uma seção `sessions` divergente; sua ausência é tolerada para que perfis,
favoritos e catálogo continuem no fluxo legado. O shadow sync normal ignora
partidas. A importação do JSON antigo só ocorre no bootstrap explícito.

O contrato escolhido é rejeitar integralmente com HTTP 409 qualquer snapshot
que tente alterar a seção `sessions`. Assim, uma alteração de perfil ou
favorito não é persistida parcialmente quando o mesmo payload contém partidas
divergentes; o cliente deve reenviar apenas o domínio legado. O frontend atual
faz isso: `saveServerState` não inclui `sessions`. Quando a seção é omitida, a
alteração legada é salva e as partidas relacionais permanecem intactas.

Após uma exclusão relacional, cópias antigas eventualmente mantidas dentro de
`app_state` não são devolvidas: `GET /state` sempre sobrepõe a seção com a
projeção relacional. Sincronizações normais também não executam o bootstrap e,
portanto, não conseguem recriar a partida excluída.

| Domínio | Fonte atual | Endpoints próprios | Situação |
|---|---|---|---|
| Eventos | Relacional | Sim | Migrado |
| Participantes de eventos | Relacional | Sim | Migrado |
| Fila de espera | Relacional | Sim | Migrado |
| Partidas/sessões | Relacional | Sim | Migrado |
| Participantes das partidas | Relacional | Sim | Migrado |
| Demais domínios | Legado | Não ou parcial | Pendente |

`app_state` continua existindo. Perfis, favoritos, comentários, catálogo e
outros domínios ainda serão migrados em etapas posteriores.
