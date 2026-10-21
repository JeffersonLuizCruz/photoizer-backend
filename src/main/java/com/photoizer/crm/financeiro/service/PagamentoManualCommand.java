package com.photoizer.crm.financeiro.service;

import com.photoizer.crm.shared.model.TipoPagamento;
import com.photoizer.crm.shared.model.FormaPagamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PagamentoManualCommand(
    BigDecimal valor,
    LocalDateTime dataPagamento,
    FormaPagamento formaPagamento,
    TipoPagamento tipoPagamento,
    String observacao
) {}
