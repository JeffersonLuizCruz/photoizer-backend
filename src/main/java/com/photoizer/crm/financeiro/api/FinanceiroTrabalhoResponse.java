package com.photoizer.crm.financeiro.api;

import com.photoizer.crm.despesa.api.DespesaResponse;
import com.photoizer.crm.financeiro.model.Pagamento;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record FinanceiroTrabalhoResponse(
    UUID agendamentoId,
    String clienteNome,
    String pacoteNome,
    BigDecimal valorCobrado,
    BigDecimal valorEntradaPago,
    BigDecimal saldoDevedor,
    BigDecimal totalRecebido,
    String statusPagamento,
    BigDecimal totalDespesas,
    BigDecimal custoDeslocamento,
    BigDecimal comissao,
    BigDecimal custoTotal,
    BigDecimal lucroBruto,
    BigDecimal margemLucro,
    List<DespesaResponse> despesas,
    List<Pagamento> pagamentos
) {}
