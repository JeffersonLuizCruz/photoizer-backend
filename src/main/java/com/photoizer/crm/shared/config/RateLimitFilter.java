package com.photoizer.crm.shared.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting simples em memória para endpoints sensíveis da galeria pública
 * (checkout, seleção de pacote, upload de comprovante e download em ZIP).
 * Mitiga enumeração, abuso e DoS localizado por IP.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;

    private static final Map<String, Integer> LIMITS = Map.of(
        "/download-zip", 5,
        "/checkout", 10,
        "/comprovante", 10,
        "/selecionar", 60,
        "/sessao", 30
    );

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(LIMITS.keySet().stream().anyMatch(path::contains)
            && (path.contains("/ecommerce/galeria/") || path.contains("/ecommerce/sessao")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        int limit = LIMITS.entrySet().stream()
            .filter(e -> path.contains(e.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(Integer.MAX_VALUE);

        String key = request.getRemoteAddr() + "|" + path;
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, current) -> {
            if (current == null || now - current.start > WINDOW_MS) {
                return new Window(now, 1);
            }
            current.count++;
            return current;
        });

        if (window.count > limit) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Muitas requisições. Tente novamente em instantes.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private static final class Window {
        private final long start;
        private int count;

        private Window(long start, int count) {
            this.start = start;
            this.count = count;
        }
    }
}
