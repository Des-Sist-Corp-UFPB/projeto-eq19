package br.com.tabula.controller;

import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.repository.FavoriteRepository;
import br.com.tabula.service.AuditLogService;
import br.com.tabula.service.AuthenticatedUserService;
import br.com.tabula.service.FavoriteService;
import br.com.tabula.service.FavoriteService.FavoriteException;
import br.com.tabula.service.FavoriteService.RequestMetadata;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.Map;
import java.util.Optional;

public final class FavoriteController {
    private FavoriteController() {}

    public static void register(Javalin app, HikariDataSource dataSource) {
        AuthenticatedUserService auth = new AuthenticatedUserService(dataSource);
        FavoriteService service = new FavoriteService(dataSource, new FavoriteRepository(),
                new AuditLogService(dataSource));
        app.get("/favorites", ctx -> authenticated(ctx, auth, service, principal ->
                ctx.json(service.list(principal))));
        app.post("/favorites/{gameId}", ctx -> authenticated(ctx, auth, service, principal -> {
            var result = service.add(principal, ctx.pathParam("gameId"), metadata(ctx));
            ctx.status(result.created() ? 201 : 200).json(Map.of("gameId", result.gameId()));
        }));
        app.delete("/favorites/{gameId}", ctx -> authenticated(ctx, auth, service, principal -> {
            service.remove(principal, ctx.pathParam("gameId"), metadata(ctx));
            ctx.status(204);
        }));
    }

    private static void authenticated(Context ctx, AuthenticatedUserService auth, FavoriteService service,
                                      Handler handler) throws Exception {
        Optional<AuthenticatedPrincipal> principal = auth.resolve(ctx.header("Authorization"));
        if (principal.isEmpty()) { ctx.status(401).json(Map.of("error", "Sessão inválida ou expirada.")); return; }
        try { handler.handle(principal.get()); }
        catch (FavoriteException ex) {
            int status = ex.kind() == FavoriteException.Kind.NOT_FOUND ? 404 : 422;
            service.auditRejected(principal.get(), ctx.pathParamMap().get("gameId"), ex.reason(), metadata(ctx));
            ctx.status(status).json(Map.of("error", ex.kind() == FavoriteException.Kind.NOT_FOUND
                    ? "Jogo não encontrado." : "Identificador de jogo inválido."));
        }
    }

    private static RequestMetadata metadata(Context ctx) { return new RequestMetadata(ctx.ip(), ctx.userAgent()); }
    @FunctionalInterface private interface Handler { void handle(AuthenticatedPrincipal principal) throws Exception; }
}
