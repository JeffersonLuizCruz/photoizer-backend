package com.photoizer.crm.shared.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import tools.jackson.databind.ObjectMapper;
import com.photoizer.crm.shared.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting com Caffeine cache para endpoints sensíveis da galeria pública.
 *
 * PATTERN: Cache-Backed Rate Limiter
 * Usa Caffeine com expireAfterWrite para eviction automática de janelas expiradas,
 * eliminando o memory leak do ConcurrentHashMap anterior.
 *
 * Correções aplicadas:
 * - Memory leak: Caffeine com maximumSize + expireAfterWrite
 * - Thread safety: AtomicInteger em vez de Window mutável
 * - Config externa: RateLimitProperties via application.properties
 * - Chave otimizada: apenas trecho do endpoint (não path completo)
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitProperties properties;
    private final Cache<String, AtomicInteger> cache;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMillis(properties.windowMs()))
            .maximumSize(properties.maximumSize())
            .build();
        log.info("RateLimitFilter initialized: windowMs={}, maximumSize={}, limits={}",
            properties.windowMs(), properties.maximumSize(), properties.limits());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean matchesEndpoint = properties.limits().keySet().stream()
            .anyMatch(path::contains);
        boolean matchesGalery = path.contains("/ecommerce/galeria/")
            || path.contains("/ecommerce/sessao");
        return !(matchesEndpoint && matchesGalery);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Extrai apenas o trecho do endpoint (ex: /checkout) em vez do path completo
        String endpoint = properties.limits().keySet().stream()
            .filter(path::contains)
            .findFirst()
            .orElse(null);

        if (endpoint == null) {
            chain.doFilter(request, response);
            return;
        }

        int limit = properties.limits().get(endpoint);
        String key = request.getRemoteAddr() + "|" + endpoint;

        AtomicInteger counter = cache.get(key, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();

        if (count > limit) {
            response.setStatus(429);
            var errorResponse = new ErrorResponse(429, "Too Many Requests", "Muitas requisições. Tente novamente em instantes.");
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getOutputStream(), errorResponse);
            return;
        }

        chain.doFilter(request, response);
    }
}
