package com.photoizer.crm.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * PATTERN: Configuration Properties
 *
 * Externaliza configurações de rate limiting via application.properties.
 * Permite ajustar limites por profile (dev mais permissivo, prod mais restritivo).
 *
 * Uso em application.properties:
 *   app.rate-limit.window-ms=60000
 *   app.rate-limit.maximum-size=10000
 *   app.rate-limit.limits./download-zip=5
 *   app.rate-limit.limits./checkout=10
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
    /** Duração da janela deslizante em milissegundos (default: 60000 = 1 minuto). */
    long windowMs,

    /** Número máximo de chaves rastreadas no cache (default: 10000). */
    long maximumSize,

    /** Limites por endpoint. Chaves são substrings de path, valores são máx de requisições por janela. */
    Map<String, Integer> limits
) {
    public RateLimitProperties {
        if (windowMs <= 0) windowMs = 60_000;
        if (maximumSize <= 0) maximumSize = 10_000;
        if (limits == null) limits = new HashMap<>();
    }
}
