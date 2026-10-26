package com.photoizer.crm.documento.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.documento.model.TipoDocumento;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * PATTERN: Strategy Pattern - Implementacao para Recibos
 *
 * Gera conteudo especifico para recibos de pagamento.
 *
 * Motivo: Isola a logica de formatacao de recibo, mantendo coesao.
 * Recibos tem estrutura diferente de contratos (menos campos, foco em valores).
 * Segue o mesmo padrao de ContratoPdfStrategy para consistencia.
 */
@Component
public class ReciboPdfStrategy implements PdfContentStrategy<Agendamento> {

    private static final DateTimeFormatter FMT_DATA_HORA =
        DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    @Override
    public TipoDocumento getTipo() {
        return TipoDocumento.RECIBO;
    }

    @Override
    public String getTitulo() {
        return "RECIBO DE PAGAMENTO";
    }

    @Override
    public List<String> getLinhas(Agendamento agendamento) {
        var cliente = agendamento.getCliente();
        var pacote = agendamento.getPacote();

        if (cliente == null || pacote == null) {
            throw new IllegalStateException(
                "Agendamento " + agendamento.getId() + " com cliente ou pacote nulo");
        }

        var linhas = new ArrayList<String>(List.of(
            "Recibo de pagamento referente ao agendamento:",
            "",
            "Cliente: " + cliente.getNome(),
            "Pacote: " + pacote.getNome(),
            "Data do ensaio: " + agendamento.getDataHoraEnsaio().format(FMT_DATA_HORA),
            "",
            "Valor total do pacote: R$ " + PdfContentHelper.formatarValor(agendamento.getValorTotal())
        ));

        if (agendamento.getValorExtras() != null
            && agendamento.getValorExtras().compareTo(BigDecimal.ZERO) > 0) {
            linhas.add("Extras: R$ " + PdfContentHelper.formatarValor(agendamento.getValorExtras()));
            linhas.add("Total final: R$ " + PdfContentHelper.formatarValor(agendamento.getValorTotalFinal()));
        }

        linhas.add("Valor pago (total): R$ " + PdfContentHelper.formatarValor(agendamento.getValorEntradaPago()));
        linhas.add("Valor restante: R$ " + PdfContentHelper.formatarValor(agendamento.getValorRestante()));
        return linhas;
    }
}
