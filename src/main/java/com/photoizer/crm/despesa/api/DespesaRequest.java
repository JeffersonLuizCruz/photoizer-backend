package com.photoizer.crm.despesa.api;

import com.photoizer.crm.despesa.model.RecorrenciaDespesa;
import com.photoizer.crm.despesa.model.StatusDespesa;
import com.photoizer.crm.shared.model.FormaPagamento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DespesaRequest(
    @NotBlank String descricao,
    @NotNull @Positive BigDecimal valor,
    @NotNull UUID categoriaId,
    @NotNull LocalDate data,
    FormaPagamento formaPagamento,
    StatusDespesa status,
    RecorrenciaDespesa recorrencia,
    UUID agendamentoId,
    String observacao
) {}
