package com.photoizer.crm.shared.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Set;

/**
 * Access log HTTP: uma linha por requisição, com o status real (inclusive
 * 401/403/429 emitidos pela SecurityFilterChain).
 *
 * <p>Não loga query string (pode conter token de galeria), headers, body nem IP.
 * O path é mascarado e tem caracteres de controle removidos, evitando que um
 * CPF/e-mail na URL vaze ou que um atacante forje linhas de log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final Set<String> IGNORAR = Set.of(
        "/actuator/health",
        "/actuator/health/liveness",
        "/actuator/health/readiness"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
            || IGNORAR.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        long inicio = System.nanoTime();
        Throwable falhaNaoTratada = null;

        try {
            chain.doFilter(request, response);
        } catch (Exception e) {
            falhaNaoTratada = e;
            throw e;
        } finally {
            long duracaoMs = (System.nanoTime() - inicio) / 1_000_000;
            int status = response.getStatus();
            var metodo = request.getMethod();
            var path = SensitiveDataMask.mask(request.getRequestURI())
                .replaceAll("[\\r\\n\\t]", "_");

            var builder = falhaNaoTratada != null || status >= 500
                ? log.atError()
                : log.atInfo();

            builder
                .addKeyValue("http_method", metodo)
                .addKeyValue("http_path", path)
                .addKeyValue("http_status", status)
                .addKeyValue("duration_ms", duracaoMs);

            var rota = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (rota != null) {
                builder.addKeyValue("http_route", rota.toString());
            }
            if (falhaNaoTratada != null) {
                builder.setCause(falhaNaoTratada);
            }

            builder.log("{} {} -> {} ({} ms)", metodo, path, status, duracaoMs);
        }
    }
}
