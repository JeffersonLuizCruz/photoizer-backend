package com.photoizer.crm.shared.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gera/reutiliza o Correlation-ID de cada requisição e o publica no MDC.
 *
 * <p>Roda antes do Spring Security ({@link Ordered#HIGHEST_PRECEDENCE}), portanto
 * está disponível para <strong>todos</strong> os logs da requisição, inclusive os
 * emitidos por filtros de segurança, rate limit e pelo GlobalExceptionHandler.
 *
 * <p>Segurança:
 * <ul>
 *   <li>o header do cliente é validado (até 64 chars, apenas {@code [A-Za-z0-9._-]}),
 *       evitando log injection / header splitting;</li>
 *   <li>valor inválido é descartado e um UUID é gerado no servidor;</li>
 *   <li>o ID não é usado para autenticação/autorização — é apenas correlação.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-ID";

    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        var header = request.getHeader(HEADER);
        var candidato = header == null ? null : header.trim();
        var correlationId = (candidato != null && VALIDO.matcher(candidato).matches())
            ? candidato
            : UUID.randomUUID().toString();

        MDC.put(LoggingKeys.CORRELATION_ID, correlationId);
        // Setado antes do chain: garante o header até em 401/403/429/500.
        response.setHeader(HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            // Threads do pool são reutilizadas: limpa correlationId, userId e userRole.
            MDC.clear();
        }
    }
}
