package com.photoizer.crm.despesa.api;

import com.photoizer.crm.despesa.model.RecorrenciaDespesa;
import com.photoizer.crm.despesa.model.StatusDespesa;
import com.photoizer.crm.shared.model.FormaPagamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DespesaResponse(
    UUID id,
    String descricao,
    BigDecimal valor,
    UUID categoriaId,
    String categoria,
    String cor,
    LocalDate data,
    FormaPagamento formaPagamento,
    StatusDespesa status,
    RecorrenciaDespesa recorrencia,
    LocalDate dataProximaGeracao,
    UUID geradaDeId,
    UUID agendamentoId,
    UUID fotografoId,
    LocalDateTime dataPagamento,
    String urlComprovante,
    String observacao
) {}
