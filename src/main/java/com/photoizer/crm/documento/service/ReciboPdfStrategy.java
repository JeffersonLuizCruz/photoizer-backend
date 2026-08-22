package com.photoizer.crm.documento.service;

import com.photoizer.crm.agenda.model.Agendamento;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * PATTERN: Strategy Pattern - Implementação para Recibos
 *
 * Gera conteúdo específico para recibos de pagamento.
 *
 * Motivo: Isola a lógica de formatação de recibo, mantendo coesão.
 * Recibos têm estrutura diferente de contratos (menos campos, foco em valores).
 * Segue o mesmo padrão de ContratoPdfStrategy para consistência.
 */
@Component
public class ReciboPdfStrategy implements PdfContentStrategy {

    @Override
    public String getTipo() {
        return "recibo";
    }

    @Override
    public String getTitulo() {
        return "RECIBO DE PAGAMENTO";
    }

    @Override
    public List<String> getLinhas(Object contexto) {
        var agendamento = (Agendamento) contexto;
        var cliente = agendamento.getCliente();
        var pacote = agendamento.getPacote();

        return List.of(
            "Recibo de pagamento referente ao agendamento:",
            "",
            "Cliente: " + cliente.getNome(),
            "Pacote: " + pacote.getNome(),
            "Data do ensaio: " + agendamento.getDataHoraEnsaio(),
            "",
            "Valor total: R$ " + formatarValor(agendamento.getValorTotal()),
            "Valor pago (entrada): R$ " + formatarValor(agendamento.getValorEntradaPago()),
            "Valor restante: R$ " + formatarValor(agendamento.getValorRestante())
        );
    }

    private String formatarValor(BigDecimal valor) {
        if (valor == null) return "0,00";
        return valor.setScale(2, RoundingMode.HALF_UP)
            .toPlainString().replace(".", ",");
    }
}
