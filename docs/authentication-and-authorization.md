# Autenticação e autorização

O backend valida o Bearer token uma única vez por requisição por meio de
`AuthenticatedUserService` e entrega um `AuthenticatedPrincipal` aos serviços de
domínio. IDs de usuário, organizador ou autor enviados no payload nunca definem
a identidade.

Nos endpoints de eventos:

- qualquer leitura pública preserva o comportamento existente;
- criação deriva o organizador exclusivamente do token;
- somente o organizador pode editar, cancelar ou concluir;
- inscrição e saída sempre afetam o próprio usuário autenticado;
- payloads malformados retornam 422, ausência/invalidade de token retorna 401,
  falta de permissão retorna 403, recurso ausente retorna 404 e conflito de
  estado retorna 409.

O serviço autoriza toda a operação antes do commit. O `PUT /api/state` continua
protegido pela autorização granular legada e não pode ser usado para contornar
as regras dos endpoints relacionais de eventos.
## Partidas

Todos os endpoints `/sessions` exigem Bearer token válido. Na criação, o
organizador é sempre o usuário autenticado; `organizerId` enviado pelo cliente
não é aceito como identidade. Participantes e vencedor precisam existir, o
vencedor precisa estar na partida e apenas o organizador (ou ADMIN conforme o
privilégio já existente) pode excluir o registro.
