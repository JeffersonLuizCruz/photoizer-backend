package com.photoizer.crm.documento.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.documento.model.TipoDocumento;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PATTERN: Strategy Pattern - Implementacao para Contratos
 *
 * Gera conteudo especifico para contratos de prestacao de servicos fotograficos.
 * Extrai dados do Agendamento e formata conforme layout definido.
 *
 * Motivo: Isola a logica de formatacao de contrato do DocumentoService,
 * permitindo mudancas de layout sem afetar outros tipos de PDF.
 * O construtor recebe o contexto tipado (Agendamento), garantindo
 * type-safety e eliminando casting generico.
 */
@Component
public class ContratoPdfStrategy implements PdfContentStrategy<Agendamento> {

    @Override
    public TipoDocumento getTipo() {
        return TipoDocumento.CONTRATO;
    }

    @Override
    public String getTitulo() {
        return "CONTRATO DE PRESTACAO DE SERVICOS FOTOGRAFICOS";
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
            "Cliente: " + cliente.getNome(),
            "CPF: " + cliente.getCpf(),
            "Telefone: " + cliente.getTelefone(),
            "",
            "Pacote: " + pacote.getNome(),
            "Data do ensaio: " + agendamento.getDataHoraEnsaio(),
            "Local: " + agendamento.getLocalEnsaio(),
            "Endereco: " + agendamento.getEnderecoCompleto(),
            "",
            "Valor total: R$ " + PdfContentHelper.formatarValor(agendamento.getValorTotal()),
            "Entrada exigida: R$ " + PdfContentHelper.formatarValor(agendamento.getValorEntradaExigido()),
            "Valor restante: R$ " + PdfContentHelper.formatarValor(agendamento.getValorRestante()),
            "Taxa de deslocamento: R$ " + PdfContentHelper.formatarValor(agendamento.getTaxaDeslocamento())
        );
    }
}
