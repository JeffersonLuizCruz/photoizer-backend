package com.photoizer.crm.financeiro.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PagamentoRequest(
    @NotNull @Positive BigDecimal valor,
    LocalDateTime dataPagamento,
    @Size(max = 500) String urlComprovante,
    @Size(max = 255) String observacao
) {}
