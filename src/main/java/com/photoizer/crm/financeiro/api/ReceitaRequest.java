package com.photoizer.crm.financeiro.api;

import com.photoizer.crm.financeiro.model.StatusReceita;
import com.photoizer.crm.financeiro.model.TipoServico;
import com.photoizer.crm.shared.model.FormaPagamento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReceitaRequest(
    UUID agendamentoId,
    UUID clienteId,
    TipoServico tipoServico,
    String descricao,
    @NotNull @Positive BigDecimal valorBruto,
    StatusReceita status,
    @PositiveOrZero BigDecimal valorRecebido,
    LocalDate dataPrevisaoRecebimento,
    LocalDateTime dataRecebimentoReal,
    FormaPagamento formaPagamento,
    String observacoes
) {}
