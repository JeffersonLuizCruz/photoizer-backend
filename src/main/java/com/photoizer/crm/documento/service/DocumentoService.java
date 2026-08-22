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
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Serviço de orquestração do módulo documento.
 * Responsável por gerar contratos e recibos em PDF para agendamentos.
 *
 * PATTERN: Event-Driven Decoupling
 * Em vez de escrever diretamente no Agendamento (escrita cross-module),
 * publica ContratoGeradoEvent para que o módulo 'agenda' (dono da máquina de estados)
 * marque o flag contratoGerado = true.
 *
 * PATTERN: Strategy Pattern
 * Utiliza PdfContentStrategy para formatação de cada tipo de PDF.
 * Permite adicionar novos tipos sem modificar esta classe (princípio Open/Closed).
 * As estratégias são descobertas automaticamente via injeção de Collection do Spring.
 *
 * Uso do Spring (Injeção de Collection):
 * O construtor recebe List<PdfContentStrategy> com todas as implementações
 * registradas como @Component. Convertemos para Map para resolução O(1) por tipo.
 * Ao criar uma nova estratégia, basta anotar com @Component e ela estará
 * disponível automaticamente sem alterar esta classe.
 */
@Service
public class DocumentoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoService.class);

    private final AgendamentoRepository agendamentoRepository;
    private final PdfWriter pdfWriter;
    private final ApplicationEventPublisher eventPublisher;

    private final Map<String, PdfContentStrategy> strategiesByTipo;

    public DocumentoService(AgendamentoRepository agendamentoRepository,
                            PdfWriter pdfWriter,
                            ApplicationEventPublisher eventPublisher,
                            List<PdfContentStrategy> strategies) {
        this.agendamentoRepository = agendamentoRepository;
        this.pdfWriter = pdfWriter;
        this.eventPublisher = eventPublisher;
        this.strategiesByTipo = strategies.stream()
            .collect(Collectors.toMap(
                PdfContentStrategy::getTipo,
                Function.identity()
            ));
    }

    /**
     * Gera PDF usando a estratégia correspondente ao tipo informado.
     *
     * @param tipo identificador da estratégia ("contrato", "recibo")
     * @param agendamentoId UUID do agendamento
     * @return bytes do PDF gerado
     */
    public byte[] gerarDocumento(String tipo, UUID agendamentoId) {
        var strategy = strategiesByTipo.get(tipo);
        if (strategy == null) {
            throw new IllegalArgumentException(
                "Tipo de documento inválido: '" + tipo + "'. Tipos disponíveis: "
                + strategiesByTipo.keySet());
        }

        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(agendamentoId));

        log.info("Gerando {} para agendamento {}", tipo, agendamentoId);

        var linhas = strategy.getLinhas(agendamento);
        var pdf = pdfWriter.gerar(strategy.getTitulo(), linhas);

        if ("contrato".equals(tipo)) {
            eventPublisher.publishEvent(new ContratoGeradoEvent(agendamentoId));
        }

        return pdf;
    }

    public byte[] gerarContrato(UUID agendamentoId) {
        return gerarDocumento("contrato", agendamentoId);
    }

    public byte[] gerarRecibo(UUID agendamentoId) {
        return gerarDocumento("recibo", agendamentoId);
    }
}
