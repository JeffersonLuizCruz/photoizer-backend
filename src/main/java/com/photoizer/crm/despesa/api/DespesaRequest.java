package com.photoizer.crm.despesa.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DespesaRequest(
    @NotBlank String descricao,
    @NotNull @Positive BigDecimal valor,
    @NotBlank String categoria,
    @NotNull LocalDate data,
    String observacao
) {}
