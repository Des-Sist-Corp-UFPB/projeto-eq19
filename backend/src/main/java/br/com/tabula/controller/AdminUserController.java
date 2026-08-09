package br.com.tabula.controller;

import br.com.tabula.model.AuditAction;
import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.service.AuditLogService;
import br.com.tabula.service.AuthenticatedUserService;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Optional;

public final class AdminUserController {
    private AdminUserController() {}

    public static void register(Javalin app, HikariDataSource dataSource) {
        AuthenticatedUserService auth = new AuthenticatedUserService(dataSource);
        AuditLogService audit = new AuditLogService(dataSource);
        app.patch("/users/{userId}/role", ctx -> authorized(ctx, auth, actor -> {
            RoleRequest request;
            try { request = ctx.bodyAsClass(RoleRequest.class); }
            catch (Exception ex) { ctx.status(422).json(Map.of("error", "Cargo inválido.")); return; }
            String role = "admin".equalsIgnoreCase(request.role()) ? "ADMIN"
                    : "student".equalsIgnoreCase(request.role()) ? "USER" : null;
            if (role == null) { ctx.status(422).json(Map.of("error", "Cargo inválido.")); return; }
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE usuarios SET role=?,atualizado_em=CURRENT_TIMESTAMP WHERE external_id=?")) {
                    statement.setString(1, role); statement.setString(2, ctx.pathParam("userId"));
                    if (statement.executeUpdate() != 1) { connection.rollback(); ctx.status(404).json(Map.of("error", "Usuário não encontrado.")); return; }
                }
                audit.record(connection, actor, AuditAction.USER_ROLE_UPDATED, "USER", ctx.pathParam("userId"), true,
                        ctx.ip(), ctx.userAgent(), Map.of("changedFields", java.util.List.of("role")));
                connection.commit(); ctx.json(Map.of("id", ctx.pathParam("userId"), "role", "ADMIN".equals(role) ? "admin" : "student"));
            }
        }));
        app.delete("/users/{userId}", ctx -> authorized(ctx, auth, actor -> {
            String id = ctx.pathParam("userId");
            if (actor.getExternalId().equals(id)) { ctx.status(409).json(Map.of("error", "Você não pode remover sua própria conta.")); return; }
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                long targetId;
                try (PreparedStatement find = connection.prepareStatement("SELECT id FROM usuarios WHERE external_id=? FOR UPDATE")) {
                    find.setString(1, id); try (ResultSet rows = find.executeQuery()) {
                        if (!rows.next()) { connection.rollback(); ctx.status(404).json(Map.of("error", "Usuário não encontrado.")); return; }
                        targetId = rows.getLong(1);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM usuarios WHERE id=?")) {
                    statement.setLong(1, targetId); statement.executeUpdate();
                }
                audit.record(connection, actor, AuditAction.USER_DELETED, "USER", id, true,
                        ctx.ip(), ctx.userAgent(), Map.of());
                connection.commit(); ctx.status(204);
            }
        }));
    }

    private static void authorized(Context ctx, AuthenticatedUserService auth, Handler handler) throws Exception {
        Optional<AuthenticatedPrincipal> actor = auth.resolve(ctx.header("Authorization"));
        if (actor.isEmpty()) { ctx.status(401).json(Map.of("error", "Sessão inválida ou expirada.")); return; }
        if (!actor.get().isAdmin()) { ctx.status(403).json(Map.of("error", "Acesso negado.")); return; }
        handler.handle(actor.get());
    }
    private record RoleRequest(String role) {}
    @FunctionalInterface private interface Handler { void handle(AuthenticatedPrincipal actor) throws Exception; }
}
