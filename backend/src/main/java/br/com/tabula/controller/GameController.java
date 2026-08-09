package br.com.tabula.controller;
import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.repository.GameRepository;
import br.com.tabula.repository.GameRepository.GameData;
import br.com.tabula.service.*;
import br.com.tabula.service.GameService.*;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.*;
public final class GameController{
 private GameController(){}
 public static void register(Javalin app,HikariDataSource ds){AuthenticatedUserService auth=new AuthenticatedUserService(ds);GameService service=new GameService(ds,new GameRepository(),new AuditLogService(ds));app.get("/games",c->c.json(service.list().stream().map(Response::from).toList()));app.get("/games/{gameId}",c->{try{c.json(Response.from(service.get(c.pathParam("gameId"))));}catch(GameException e){error(c,e);}});app.post("/games",c->authenticated(c,auth,service,p->{Request r=body(c);c.status(201).json(Response.from(service.create(p,r.input(),meta(c))));}));app.put("/games/{gameId}",c->authenticated(c,auth,service,p->{Request r=body(c);c.json(Response.from(service.update(p,c.pathParam("gameId"),r.input(),meta(c))));}));app.delete("/games/{gameId}",c->authenticated(c,auth,service,p->{service.delete(p,c.pathParam("gameId"),meta(c));c.status(204);}));}
 private static Request body(Context c)throws GameException{try{return c.bodyAsClass(Request.class);}catch(Exception e){throw GameException.invalid("invalid_payload");}}
 private static void authenticated(Context c,AuthenticatedUserService a,GameService s,Handler h)throws Exception{Optional<AuthenticatedPrincipal> p=a.resolve(c.header("Authorization"));if(p.isEmpty()){c.status(401).json(Map.of("error","Sessão inválida ou expirada."));return;}try{h.handle(p.get());}catch(GameException e){s.auditRejected(p.get(),c.pathParamMap().get("gameId"),e.reason(),meta(c));error(c,e);}}
 private static void error(Context c,GameException e){int status=switch(e.kind()){case NOT_FOUND->404;case FORBIDDEN->403;case CONFLICT->409;case INVALID->422;};c.status(status).json(Map.of("error",switch(e.kind()){case NOT_FOUND->"Jogo não encontrado.";case FORBIDDEN->"Acesso negado.";case CONFLICT->"Jogo possui vínculos e não pode ser removido.";case INVALID->"Dados do jogo inválidos.";}));}
 private static RequestMetadata meta(Context c){return new RequestMetadata(c.ip(),c.userAgent());}
 private record Request(String name,String description,String coverUrl,String category,int minPlayers,int maxPlayers,int avgPlayTime,double complexity){GameInput input(){return new GameInput(name,description,coverUrl,category,minPlayers,maxPlayers,avgPlayTime,complexity);}}
 private record Response(String id,String name,String description,String coverUrl,String category,int minPlayers,int maxPlayers,int avgPlayTime,double complexity){static Response from(GameData g){return new Response(g.id(),g.name(),g.description(),g.coverUrl(),g.category(),g.minPlayers(),g.maxPlayers(),g.avgPlayTime(),g.complexity());}}
 @FunctionalInterface private interface Handler{void handle(AuthenticatedPrincipal p)throws Exception;}
}
