package com.photoizer.crm.documento.service;

import com.photoizer.crm.agenda.event.ContratoGeradoEvent;
import com.photoizer.crm.agenda.exception.AgendamentoNaoEncontradoException;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.shared.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Serviço de orquestração do módulo documento.
 * Responsável por gerar contratos e recibos em PDF para agendamentos.
 *
 * PATTERN: Event-Driven Decoupling
 * Em vez de escrever diretamente no Agendamento (escrita cross-module),
 * publica ContratoGeradoEvent para que o módulo 'agenda' (dono da máquina de estados)
 * marque o flag contratoGerado = true.
 */
@Service
public class DocumentoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoService.class);

    private final AgendamentoRepository agendamentoRepository;
    private final PdfWriter pdfWriter;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentoService(AgendamentoRepository agendamentoRepository,
                            PdfWriter pdfWriter,
                            ApplicationEventPublisher eventPublisher) {
        this.agendamentoRepository = agendamentoRepository;
        this.pdfWriter = pdfWriter;
        this.eventPublisher = eventPublisher;
    }

    public byte[] gerarContrato(UUID agendamentoId) {
        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(agendamentoId));

        log.info("Gerando contrato para agendamento {}", agendamentoId);

        var linhas = montarLinhasContrato(agendamento);
        var pdf = pdfWriter.gerar("CONTRATO DE PRESTAÇÃO DE SERVIÇOS FOTOGRÁFICOS", linhas);

        eventPublisher.publishEvent(new ContratoGeradoEvent(agendamentoId));

        return pdf;
    }

    public byte[] gerarRecibo(UUID agendamentoId) {
        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(agendamentoId));

        log.info("Gerando recibo para agendamento {}", agendamentoId);

        var linhas = montarLinhasRecibo(agendamento);
        return pdfWriter.gerar("RECIBO DE PAGAMENTO", linhas);
    }

    private List<String> montarLinhasContrato(com.photoizer.crm.agenda.model.Agendamento agendamento) {
        return List.of(
            "Cliente: " + agendamento.getCliente().getNome(),
            "CPF: " + agendamento.getCliente().getCpf(),
            "Telefone: " + agendamento.getCliente().getTelefone(),
            "",
            "Pacote: " + agendamento.getPacote().getNome(),
            "Data do ensaio: " + agendamento.getDataHoraEnsaio(),
            "Local: " + agendamento.getLocalEnsaio(),
            "Endereço: " + agendamento.getEnderecoCompleto(),
            "",
            "Valor total: R$ " + formatarValor(agendamento.getValorTotal()),
            "Entrada exigida: R$ " + formatarValor(agendamento.getValorEntradaExigido()),
            "Valor restante: R$ " + formatarValor(agendamento.getValorRestante()),
            "Taxa de deslocamento: R$ " + formatarValor(agendamento.getTaxaDeslocamento())
        );
    }

    private List<String> montarLinhasRecibo(com.photoizer.crm.agenda.model.Agendamento agendamento) {
        return List.of(
            "Recibo de pagamento referente ao agendamento:",
            "",
            "Cliente: " + agendamento.getCliente().getNome(),
            "Pacote: " + agendamento.getPacote().getNome(),
            "Data do ensaio: " + agendamento.getDataHoraEnsaio(),
            "",
            "Valor total: R$ " + formatarValor(agendamento.getValorTotal()),
            "Valor pago (entrada): R$ " + formatarValor(agendamento.getValorEntradaPago()),
            "Valor restante: R$ " + formatarValor(agendamento.getValorRestante())
        );
    }

    private String formatarValor(java.math.BigDecimal valor) {
        if (valor == null) return "0,00";
        return valor.setScale(2, java.math.RoundingMode.HALF_UP)
            .toPlainString().replace(".", ",");
    }
}
