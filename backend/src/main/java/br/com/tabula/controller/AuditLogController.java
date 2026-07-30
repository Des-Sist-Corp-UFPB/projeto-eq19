package br.com.tabula.controller;

import br.com.tabula.dto.AuditLogFilter;
import br.com.tabula.dto.AuditLogPageResponse;
import br.com.tabula.dto.AuditLogResponse;
import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.repository.AuditLogRepository;
import br.com.tabula.service.AuthenticatedUserService;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AuditLogController {
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private AuditLogController() {
    }

    public static void register(Javalin app, HikariDataSource dataSource) {
        AuditLogRepository repository = new AuditLogRepository(dataSource);
        AuthenticatedUserService authenticatedUserService = new AuthenticatedUserService(dataSource);

        app.get("/audit-logs", ctx -> {
            Optional<AuthenticatedPrincipal> principal =
                    authenticatedUserService.resolve(ctx.header("Authorization"));
            if (principal.isEmpty()) {
                ctx.status(401).json(Map.of("error", "Sessão inválida ou expirada."));
                return;
            }
            if (!principal.get().isAdmin()) {
                ctx.status(403).json(Map.of("error", "Acesso restrito a administradores."));
                return;
            }

            try {
                AuditLogFilter filter = parseFilter(ctx);
                List<AuditLogResponse> items = repository.findPage(filter).stream()
                        .map(AuditLogResponse::new)
                        .toList();
                long total = repository.count(filter);
                ctx.json(new AuditLogPageResponse(items, filter.getPage(), filter.getPageSize(), total));
            } catch (IllegalArgumentException ex) {
                ctx.status(400).json(Map.of("error", ex.getMessage()));
            }
        });
    }

    static AuditLogFilter parseFilter(Context ctx) {
        int page = parsePositiveInt(ctx.queryParam("page"), 1, "page");
        int requestedPageSize = parsePositiveInt(ctx.queryParam("pageSize"), DEFAULT_PAGE_SIZE, "pageSize");
        int pageSize = Math.min(requestedPageSize, MAX_PAGE_SIZE);

        String action = clean(ctx.queryParam("action"), 80, "action");
        if (action != null && !action.matches("[A-Z_]+")) {
            throw new IllegalArgumentException("action inválida.");
        }
        String userId = clean(ctx.queryParam("userId"), 80, "userId");
        String resourceType = clean(ctx.queryParam("resourceType"), 80, "resourceType");
        String resourceId = clean(ctx.queryParam("resourceId"), 120, "resourceId");
        Boolean success = parseBoolean(ctx.queryParam("success"));
        Instant startDate = parseInstant(ctx.queryParam("startDate"), "startDate");
        Instant endDate = parseInstant(ctx.queryParam("endDate"), "endDate");
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate deve ser anterior a endDate.");
        }
        return new AuditLogFilter(
                page, pageSize, action, userId, resourceType, resourceId, success, startDate, endDate
        );
    }

    private static int parsePositiveInt(String value, int defaultValue, String name) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) throw new IllegalArgumentException(name + " deve ser maior que zero.");
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " inválida.");
        }
    }

    private static Boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) return null;
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("success inválido.");
    }

    private static Instant parseInstant(String value, String name) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(name + " inválida.");
        }
    }

    private static String clean(String value, int maxLength, String name) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.trim();
        if (cleaned.length() > maxLength) throw new IllegalArgumentException(name + " excede o limite.");
        return cleaned;
    }
}
