package com.photoizer.crm.ecommerce.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(name = "AtualizarConfiguracaoPagamentoRequest",
    description = "Configuracao do PIX exibido no checkout de fotos extras.")
public record AtualizarConfiguracaoPagamentoRequest(
    @Schema(description = "Chave PIX (CPF, CNPJ, e-mail, telefone ou aleatoria)",
        example = "12.345.678/0001-90", nullable = true)
    @Size(max = 200)
    String pixChave,

    @Schema(description = "Tipo da chave PIX", example = "CNPJ", nullable = true)
    @Size(max = 20)
    String pixTipoChave
) {}
