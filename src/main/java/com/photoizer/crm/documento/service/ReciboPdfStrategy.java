package com.photoizer.crm.documento.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.documento.model.TipoDocumento;
import org.springframework.stereotype.Component;

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

        return List.of(
            "Recibo de pagamento referente ao agendamento:",
            "",
            "Cliente: " + cliente.getNome(),
            "Pacote: " + pacote.getNome(),
            "Data do ensaio: " + agendamento.getDataHoraEnsaio(),
            "",
            "Valor total: R$ " + PdfContentHelper.formatarValor(agendamento.getValorTotal()),
            "Valor pago (entrada): R$ " + PdfContentHelper.formatarValor(agendamento.getValorEntradaPago()),
            "Valor restante: R$ " + PdfContentHelper.formatarValor(agendamento.getValorRestante())
        );
    }
}
