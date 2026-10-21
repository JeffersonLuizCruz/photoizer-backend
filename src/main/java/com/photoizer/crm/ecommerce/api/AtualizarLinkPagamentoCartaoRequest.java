package com.photoizer.crm.ecommerce.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(name = "AtualizarLinkPagamentoCartaoRequest",
    description = "Link de pagamento por cartao de credito de uma compra de fotos extras. "
        + "Envie vazio ou nulo para remover o link.")
public record AtualizarLinkPagamentoCartaoRequest(
    @Schema(description = "URL de pagamento (http/https)", example = "https://pagamento.exemplo.com/abc123",
        nullable = true)
    @Size(max = 1000)
    String linkPagamentoCartao
) {}
