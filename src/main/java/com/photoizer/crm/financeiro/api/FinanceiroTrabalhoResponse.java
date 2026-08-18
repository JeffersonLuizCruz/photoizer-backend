package com.photoizer.crm.financeiro.api;

import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.shared.model.TipoRepasse;
import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.despesa.api.DespesaResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    List<FotografoInfo> fotografos,
    BigDecimal valorPartilhaGlobal,
    BigDecimal valorLucroCrm,
    BigDecimal totalCustosFotografo,
    List<DespesaResponse> despesas,
    List<DespesaResponse> custosFotografo,
    List<PagamentoResponse> pagamentos
) {
    public record FotografoInfo(
        UUID fotografoId,
        String fotografoNome,
        BigDecimal custos,
        BigDecimal valorRepassar,
        RepasseStatus statusRepasse,
        LocalDateTime dataPagamento,
        TipoRepasse tipoValor,
        BigDecimal percentual,
        Papel papelParceiro
    ) {}
}
