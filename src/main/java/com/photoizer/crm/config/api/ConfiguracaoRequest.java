package com.photoizer.crm.config.api;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request para atualizacao de configuracoes.
 *
 * Pattern: DTO como Record — imutavel, validavel com @Valid,
 * separa contrato da API da entidade JPA.
 *
 * Aceita Map<String, String> para compatibilidade com o frontend,
 * que envia chaves como "percentualEntrada" em vez de "PERCENTUAL_ENTRADA".
 * A validacao contra ConfigKey ocorre no service layer.
 */
public record ConfiguracaoRequest(
    @NotNull Map<String, String> valores
) {}
