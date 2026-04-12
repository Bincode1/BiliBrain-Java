package com.bin.bilibrain.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import java.util.UUID;

@Component
public class ApiRequestLoggingInterceptor implements AsyncHandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(ApiRequestLoggingInterceptor.class);
    private static final String ATTR_REQUEST_ID = ApiRequestLoggingInterceptor.class.getName() + ".requestId";
    private static final String ATTR_START_NANOS = ApiRequestLoggingInterceptor.class.getName() + ".startNanos";
    private static final String ATTR_COMPLETED = ApiRequestLoggingInterceptor.class.getName() + ".completed";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getAttribute(ATTR_REQUEST_ID) == null) {
            request.setAttribute(ATTR_REQUEST_ID, resolveRequestId(request));
        }
        if (request.getAttribute(ATTR_START_NANOS) == null) {
            request.setAttribute(ATTR_START_NANOS, System.nanoTime());
        }
        if (request.getDispatcherType() != DispatcherType.ASYNC) {
            log.info(
                "[{}] -> {} {}{} from {}",
                requestId(request),
                request.getMethod(),
                request.getRequestURI(),
                formatQuery(request),
                request.getRemoteAddr()
            );
        }
        return true;
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        log.info("[{}] .. async started {} {}", requestId(request), request.getMethod(), request.getRequestURI());
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        @Nullable Exception ex
    ) {
        if (Boolean.TRUE.equals(request.getAttribute(ATTR_COMPLETED))) {
            return;
        }
        request.setAttribute(ATTR_COMPLETED, true);
        long durationMs = elapsedMillis(request);
        if (ex == null) {
            log.info(
                "[{}] <- {} {}{} status={} {}ms",
                requestId(request),
                request.getMethod(),
                request.getRequestURI(),
                formatQuery(request),
                response.getStatus(),
                durationMs
            );
            return;
        }
        log.warn(
            "[{}] <- {} {}{} status={} {}ms ex={}: {}",
            requestId(request),
            request.getMethod(),
            request.getRequestURI(),
            formatQuery(request),
            response.getStatus(),
            durationMs,
            ex.getClass().getSimpleName(),
            ex.getMessage()
        );
    }

    private String resolveRequestId(HttpServletRequest request) {
        String headerValue = request.getHeader("X-Request-Id");
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue.trim();
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(ATTR_REQUEST_ID);
        return value == null ? "unknown" : String.valueOf(value);
    }

    private long elapsedMillis(HttpServletRequest request) {
        Object startedAt = request.getAttribute(ATTR_START_NANOS);
        if (!(startedAt instanceof Long startNanos)) {
            return -1L;
        }
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private String formatQuery(HttpServletRequest request) {
        String query = request.getQueryString();
        return (query == null || query.isBlank()) ? "" : "?" + query;
    }
}
