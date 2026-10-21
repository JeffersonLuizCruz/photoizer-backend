package com.photoizer.crm.financeiro.api;

import com.photoizer.crm.shared.model.FormaPagamento;
import com.photoizer.crm.shared.model.TipoPagamento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PagamentoRequest(
    @NotNull @Positive BigDecimal valor,
    LocalDateTime dataPagamento,
    @Size(max = 500) String urlComprovante,
    @Size(max = 255) String observacao,
    FormaPagamento formaPagamento,
    TipoPagamento tipoPagamento
) {}
