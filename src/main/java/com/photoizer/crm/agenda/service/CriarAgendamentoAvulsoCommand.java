package com.photoizer.crm.agenda.service;

import com.photoizer.crm.shared.model.FormaPagamento;
import com.photoizer.crm.shared.model.TipoPagamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CriarAgendamentoAvulsoCommand(
    String clienteNome,
    String clienteTelefone,
    String clienteEmail,
    String clienteCpf,
    String clienteCidade,
    String clienteEstado,
    UUID pacoteId,
    UUID fotografoId,
    LocalDateTime dataHoraEnsaio,
    Integer duracaoMinutos,
    String localEnsaio,
    BigDecimal valorTotal,
    BigDecimal valorEntradaExigido,
    BigDecimal valorPago,
    LocalDateTime dataPagamento,
    FormaPagamento formaPagamento,
    TipoPagamento tipoPagamento,
    BigDecimal custoDeslocamento,
    Boolean repassarDeslocamento,
    Boolean autorizaUsoImagem,
    String observacoes
) {}
