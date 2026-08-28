package com.photoizer.crm.financeiro.api;

import com.photoizer.crm.agenda.model.Agendamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO próprio do financeiro para dados de agendamento nos relatórios.
 *
 * Pattern: Anti-Corruption Layer — elimina dependência direta do financeiro
 * com AgendamentoResponse do agenda. Cada módulo define seu próprio contrato.
 */
public record RelatorioAgendamentoItem(
    UUID id,
    String clienteNome,
    String pacoteNome,
    BigDecimal valorTotal,
    BigDecimal valorEntradaPago,
    BigDecimal valorRestante,
    BigDecimal valorExtras,
    BigDecimal valorTotalFinal,
    LocalDateTime dataHoraEnsaio,
    String status
) {
    public static RelatorioAgendamentoItem of(Agendamento a) {
        return new RelatorioAgendamentoItem(
            a.getId(),
            a.getCliente().getNome(),
            a.getPacote() != null ? a.getPacote().getNome() : null,
            a.getValorTotal(),
            a.getValorEntradaPago(),
            a.getValorRestante(),
            a.getValorExtras(),
            a.getValorTotalFinal(),
            a.getDataHoraEnsaio(),
            a.getStatus().name()
        );
    }
}
