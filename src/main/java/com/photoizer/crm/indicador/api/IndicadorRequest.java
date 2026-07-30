package com.photoizer.crm.indicador.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record IndicadorRequest(
    @NotBlank String nome,
    @NotBlank String telefone,
    String observacoes,
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    BigDecimal percentualComissao
) {}
