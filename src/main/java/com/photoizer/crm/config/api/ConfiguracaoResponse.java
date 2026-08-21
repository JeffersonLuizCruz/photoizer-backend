package com.photoizer.crm.config.api;

import com.photoizer.crm.config.model.ConfigKey;

import java.util.Map;
import java.util.TreeMap;

/**
 * Response com todas as configuracoes do sistema.
 *
 * Pattern: DTO como Record — imutavel, esconde a entidade JPA.
 * O Map usa String como key para manter compatibilidade com o frontend,
 * mas as keys sao centralizadas no ConfigKey.
 */
public record ConfiguracaoResponse(Map<String, String> configuracoes) {

    /**
     * Factory method que monta a response a partir de um Map interno do service.
     */
    public static ConfiguracaoResponse of(Map<String, String> configs) {
        var sorted = new TreeMap<>(configs);
        return new ConfiguracaoResponse(Map.copyOf(sorted));
    }
}
