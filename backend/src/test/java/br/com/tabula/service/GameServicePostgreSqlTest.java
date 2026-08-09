package br.com.tabula.service;

import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.repository.GameRepository;
import br.com.tabula.repository.GameRepository.GameData;
import br.com.tabula.controller.GameController;
import br.com.tabula.service.GameService.*;
import com.zaxxer.hikari.*;
import io.javalin.Javalin;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.sql.*;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GameServicePostgreSqlTest {
 @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("tabula_games_test").withUsername("tabula").withPassword("tabula");
 static HikariDataSource ds; static AuthenticatedPrincipal admin,user; static GameService service;
 @BeforeAll static void migrate()throws Exception{
  Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()).target("12").load().migrate();
  try(Connection c=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());Statement s=c.createStatement()){
   s.executeUpdate("INSERT INTO jogos(external_id,nome,descricao,cover_url,categoria,min_players,max_players,avg_play_time,complexity) VALUES('kept','Relational','Official','/official.png','Strategy',2,4,60,3.0)");
   s.executeUpdate("INSERT INTO app_state(id,data) VALUES(1,'{\"boardGames\":[{\"id\":\"kept\",\"name\":\"Legacy overwrite\",\"description\":\"Legacy\",\"coverUrl\":\"/legacy.png\",\"category\":\"Other\",\"minPlayers\":1,\"maxPlayers\":9,\"avgPlayTime\":10,\"complexity\":1},{\"id\":\"backfilled\",\"name\":\"Backfilled\",\"description\":\"Imported\",\"coverUrl\":\"/backfilled.png\",\"category\":\"Cards\",\"minPlayers\":2,\"maxPlayers\":2,\"avgPlayTime\":30,\"complexity\":2}]}'::jsonb)");
  }
  Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()).load().migrate();
  HikariConfig h=new HikariConfig();h.setJdbcUrl(POSTGRES.getJdbcUrl());h.setUsername(POSTGRES.getUsername());h.setPassword(POSTGRES.getPassword());ds=new HikariDataSource(h);
  try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.executeUpdate("INSERT INTO usuarios(external_id,nome,email,senha_hash,role,email_verificado) VALUES('admin-game','Admin','admin-game@test','x','ADMIN',true),('user-game','User','user-game@test','x','USER',true)");s.executeUpdate("INSERT INTO auth_tokens(token,usuario_id,expires_at) SELECT external_id||'-token',id,CURRENT_TIMESTAMP+INTERVAL '1 hour' FROM usuarios WHERE external_id IN('admin-game','user-game')");try(ResultSet r=s.executeQuery("SELECT id,external_id,role FROM usuarios WHERE external_id IN('admin-game','user-game') ORDER BY external_id")){r.next();admin=new AuthenticatedPrincipal(r.getLong(1),r.getString(2),r.getString(3));r.next();user=new AuthenticatedPrincipal(r.getLong(1),r.getString(2),r.getString(3));}}
  service=new GameService(ds,new GameRepository(),new AuditLogService(ds));
 }
 @AfterAll static void close(){if(ds!=null)ds.close();}
 @Test @Order(1) void upgradesV12BackfillsWithoutOverwritingOrDuplicates()throws Exception{assertEquals("Relational",service.get("kept").name());assertEquals("Official",service.get("kept").description());assertEquals("Backfilled",service.get("backfilled").name());try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT count(*) FROM jogos WHERE external_id='backfilled'")){assertTrue(r.next());assertEquals(1,r.getInt(1));}}
 @Test @Order(2) void supportsAuthorizedCrudAndPreservesExternalId()throws Exception{GameInput input=new GameInput("API Game","Description","/cover.png","Strategy",2,5,45,2.5);GameData created=service.create(admin,input,new RequestMetadata("127.0.0.1","test"));assertTrue(created.id().startsWith("g_"));assertEquals(created.id(),service.get(created.id()).id());GameData updated=service.update(admin,created.id(),new GameInput("API Game 2","Description","/cover.png","Strategy",2,5,45,2.5),new RequestMetadata(null,null));assertEquals("API Game 2",updated.name());service.delete(admin,created.id(),new RequestMetadata(null,null));assertThrows(GameException.class,()->service.get(created.id()));}
 @Test @Order(3) void rejectsNonAdminAndKeepsReferencedGames()throws Exception{GameInput input=new GameInput("Nope","Description","/cover.png","Strategy",2,3,30,2);assertEquals(GameException.Kind.FORBIDDEN,assertThrows(GameException.class,()->service.create(user,input,new RequestMetadata(null,null))).kind());try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.executeUpdate("INSERT INTO favoritos(usuario_id,jogo_id) SELECT "+user.getDatabaseId()+",id FROM jogos WHERE external_id='kept'");}assertEquals(GameException.Kind.CONFLICT,assertThrows(GameException.class,()->service.delete(admin,"kept",new RequestMetadata(null,null))).kind());assertEquals("kept",service.get("kept").id());}
 @Test @Order(4) void exposesPublicReadsAndAuthorizedAdminEndpoints()throws Exception{Javalin app=Javalin.create();GameController.register(app,ds);app.start(0);try{assertEquals(200,call(app,"GET","/games",null,null).statusCode());assertEquals(200,call(app,"GET","/games/kept",null,null).statusCode());assertEquals(404,call(app,"GET","/games/missing",null,null).statusCode());String body="{\"name\":\"HTTP Game\",\"description\":\"Description\",\"coverUrl\":\"/http.png\",\"category\":\"Cards\",\"minPlayers\":2,\"maxPlayers\":4,\"avgPlayTime\":30,\"complexity\":2}";assertEquals(401,call(app,"POST","/games",body,null).statusCode());assertEquals(403,call(app,"POST","/games",body,"user-game-token").statusCode());HttpResponse<String> created=call(app,"POST","/games",body,"admin-game-token");assertEquals(201,created.statusCode());String id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(created.body()).path("id").asText();assertEquals(200,call(app,"PUT","/games/"+id,body.replace("HTTP Game","HTTP Updated"),"admin-game-token").statusCode());assertEquals(204,call(app,"DELETE","/games/"+id,null,"admin-game-token").statusCode());assertEquals(422,call(app,"POST","/games","{}","admin-game-token").statusCode());}finally{app.stop();}}
 private static HttpResponse<String> call(Javalin app,String method,String path,String body,String token)throws Exception{HttpRequest.Builder b=HttpRequest.newBuilder(URI.create("http://localhost:"+app.port()+path));if(token!=null)b.header("Authorization","Bearer "+token);if(body!=null)b.header("Content-Type","application/json");b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));return HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofString());}
}
