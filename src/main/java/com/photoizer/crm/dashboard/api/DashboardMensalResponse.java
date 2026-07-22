package com.photoizer.crm.dashboard.api;

import java.math.BigDecimal;
import java.util.List;

public record DashboardMensalResponse(
    ResumoMesAtual mesAtual,
    List<DadosMensais> historico
) {
    public record ResumoMesAtual(
        int totalAgendamentos,
        int confirmados,
        BigDecimal valorTotalConfirmados,
        BigDecimal entradasRecebidas,
        BigDecimal saldoRestante,
        int finalizados,
        BigDecimal valorTotalFinalizados,
        BigDecimal despesasDeslocamento,
        BigDecimal despesasComissao,
        BigDecimal despesasManuais,
        BigDecimal saldoLiquido,
        BigDecimal receitaProjetada,
        BigDecimal liquidoAtual,
        BigDecimal liquidoPrevisto
    ) {}

    public record DadosMensais(
        String mes,
        int totalAgendamentos,
        BigDecimal valorConfirmados,
        BigDecimal valorFinalizados,
        BigDecimal despesasDeslocamento,
        BigDecimal despesasComissao,
        BigDecimal despesasManuais,
        BigDecimal entradasRecebidas,
        BigDecimal liquidoAtual,
        BigDecimal liquidoPrevisto
    ) {}
}
