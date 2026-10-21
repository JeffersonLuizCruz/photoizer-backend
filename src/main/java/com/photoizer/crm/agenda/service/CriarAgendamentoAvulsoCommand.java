package com.photoizer.crm.agenda.service;

import com.photoizer.crm.shared.model.FormaPagamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CriarAgendamentoAvulsoCommand(
    UUID pacoteId,
    UUID fotografoId,
    LocalDateTime dataHoraEnsaio,
    Integer duracaoMinutos,
    String localEnsaio,
    BigDecimal percentualEntrada,
    BigDecimal valorJaRecebido,
    LocalDateTime dataRecebimento,
    FormaPagamento formaPagamento,
    BigDecimal custoDeslocamento,
    Boolean repassarDeslocamento,
    String observacoes
) {}
