package com.photoizer.crm.ecommerce.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CupomRequest(
    @NotBlank String codigo,
    String descricao,
    @NotBlank String tipoDesconto,
    @NotNull @Positive BigDecimal valorDesconto,
    BigDecimal valorMinimoPedido,
    Integer usoLimite,
    LocalDate dataValidade,
    boolean ativo,
    boolean usoUnico
) {}
