package com.photoizer.crm.ecommerce.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ConfiguracaoPagamentoResponse",
    description = "Dados de PIX exibidos no checkout de fotos extras.")
public record ConfiguracaoPagamentoResponse(
    @Schema(description = "Chave PIX configurada para o e-commerce", example = "12.345.678/0001-90")
    String pixChave,
    @Schema(description = "Tipo da chave PIX", example = "CNPJ")
    String pixTipoChave
) {}
