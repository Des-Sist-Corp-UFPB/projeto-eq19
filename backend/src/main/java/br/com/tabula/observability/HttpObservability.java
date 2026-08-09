package br.com.tabula.observability;

import io.javalin.Javalin;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HttpObservability {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpObservability.class);
    private static final String ATTRIBUTE = HttpObservability.class.getName() + ".request";
    private static final String ROUTE_ATTRIBUTE = HttpObservability.class.getName() + ".route";

    private HttpObservability() {
    }

    public static void register(Javalin app) {
        app.before(ctx -> {
            String method = ctx.method().name();
            String path = ctx.path();
            Span span = Span.current();
            span.setAttribute("http.request.method", method);
            span.setAttribute("url.path", path);
            ctx.attribute(ATTRIBUTE, new RequestTrace(span, System.nanoTime(), method, path));
        });

        app.beforeMatched(ctx -> {
            String route = ctx.matchedPath();
            ctx.attribute(ROUTE_ATTRIBUTE, route);
            HttpServerRoute.update(Context.current(), HttpServerRouteSource.CONTROLLER, route);
        });

        app.after(ctx -> {
            RequestTrace request = ctx.attribute(ATTRIBUTE);
            if (request == null) return;
            int status = ctx.statusCode();
            long durationMs = Math.max(0L, (System.nanoTime() - request.startedAtNanos()) / 1_000_000L);
            String route = normalizedRoute(ctx.attribute(ROUTE_ATTRIBUTE));
            request.span().updateName(request.method() + " " + route);
            request.span().setAttribute("http.route", route);
            request.span().setAttribute("http.response.status_code", status);
            request.span().setAttribute("http.server.request.duration_ms", durationMs);
            if (status >= 500) request.span().setStatus(StatusCode.ERROR, "server error");
            else if (status >= 400) request.span().setAttribute("http.response.is_client_error", true);

            if (!"/ping".equals(request.path())) {
                LOGGER.atInfo()
                        .addKeyValue("http_method", request.method())
                        .addKeyValue("http_route", route)
                        .addKeyValue("http_status", status)
                        .addKeyValue("duration_ms", durationMs)
                        .addKeyValue("trace_id", request.span().getSpanContext().getTraceId())
                        .log("HTTP request completed");
            }
        });
    }

    static String normalizedRoute(String matchedPath) {
        return matchedPath == null || matchedPath.isBlank() ? "unmatched" : matchedPath;
    }

    private record RequestTrace(
            Span span, long startedAtNanos, String method, String path) {
    }
}
