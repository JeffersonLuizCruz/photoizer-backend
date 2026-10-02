package com.photoizer.crm.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuração de CORS externalizada por ambiente.
 *
 * <p>Uso em {@code application.properties}:
 * <pre>
 * app.cors.allowed-origins=http://localhost:5173,http://127.0.0.1:5173
 * </pre>
 *
 * <p>Referência: achado A5 do relatório (evitar origens hardcoded e permitir
 * configuração explícita em homolog/prod).
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
    /** Origens permitidas (scheme + host + porta). Vazio = nenhuma origem remota. */
    List<String> allowedOrigins
) {
    public CorsProperties {
        if (allowedOrigins == null) {
            allowedOrigins = List.of();
        }
    }
}
