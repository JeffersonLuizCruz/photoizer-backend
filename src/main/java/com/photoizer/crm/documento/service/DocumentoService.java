package com.photoizer.crm.documento.service;

import com.photoizer.crm.agenda.event.ContratoGeradoEvent;
import com.photoizer.crm.agenda.exception.AgendamentoNaoEncontradoException;
import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.documento.model.TipoComprovante;
import com.photoizer.crm.documento.model.TipoDocumento;
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
 * Servico de orquestracao do modulo documento.
 * Responsavel por gerar contratos e recibos em PDF para agendamentos,
 * e servir comprovantes de pagamento.
 *
 * PATTERN: Event-Driven Decoupling
 * Em vez de escrever diretamente no Agendamento (escrita cross-module),
 * publica ContratoGeradoEvent para que o modulo 'agenda' (dono da maquina de estados)
 * marque o flag contratoGerado = true.
 *
 * PATTERN: Strategy Pattern (Generic)
 * Utiliza PdfContentStrategy<T> para formatacao de cada tipo de PDF.
 * Permite adicionar novos tipos sem modificar esta classe (principio Open/Closed).
 * As estrategias sao descobertas automaticamente via injecao de Collection do Spring.
 *
 * PATTERN: Facade (Download de Comprovante)
 * Centraliza a logica de resolucao de comprovantes (antes espalhada no controller).
 * Elimina acoplamento do controller com AgendamentoRepository.
 */
@Service
public class DocumentoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoService.class);

    private final AgendamentoRepository agendamentoRepository;
    private final PdfWriter pdfWriter;
    private final ApplicationEventPublisher eventPublisher;

    private final Map<TipoDocumento, PdfContentStrategy<?>> strategiesByTipo;

    public DocumentoService(AgendamentoRepository agendamentoRepository,
                            PdfWriter pdfWriter,
                            ApplicationEventPublisher eventPublisher,
                            List<PdfContentStrategy<?>> strategies) {
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
     * Gera PDF usando a estrategia correspondente ao tipo informado.
     *
     * @param tipo identificador do tipo de documento
     * @param agendamentoId UUID do agendamento
     * @return bytes do PDF gerado
     */
    @SuppressWarnings("unchecked")
    public byte[] gerarDocumento(TipoDocumento tipo, UUID agendamentoId) {
        var strategy = strategiesByTipo.get(tipo);
        if (strategy == null) {
            throw new IllegalArgumentException(
                "Tipo de documento invalido: '" + tipo + "'. Tipos disponiveis: "
                + strategiesByTipo.keySet());
        }

        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(agendamentoId));

        log.info("Gerando {} para agendamento {}", tipo.getValor(), agendamentoId);

        var linhas = ((PdfContentStrategy<Agendamento>) strategy).getLinhas(agendamento);
        var pdf = pdfWriter.gerar(strategy.getTitulo(), linhas);

        if (tipo == TipoDocumento.CONTRATO) {
            eventPublisher.publishEvent(new ContratoGeradoEvent(agendamentoId));
        }

        return pdf;
    }

    public byte[] gerarContrato(UUID agendamentoId) {
        return gerarDocumento(TipoDocumento.CONTRATO, agendamentoId);
    }

    public byte[] gerarRecibo(UUID agendamentoId) {
        return gerarDocumento(TipoDocumento.RECIBO, agendamentoId);
    }

    /**
     * Resolve o caminho do comprovamento de pagamento (entrada ou final) para um agendamento.
     *
     * PATTERN: Facade
     * Centraliza a logica de resolucao de comprovante que antes estava espalhada
     * no DocumentoController (que injetava AgendamentoRepository diretamente).
     *
     * @param agendamentoId UUID do agendamento
     * @param tipo "entrada" ou "final"
     * @return CaminhoComprovante com filename e caminho, ou null se nao encontrado
     */
    public CaminhoComprovante resolverComprovante(UUID agendamentoId, String tipo) {
        var tipoComprovante = TipoComprovante.fromValor(tipo);

        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(agendamentoId));

        var caminho = tipoComprovante.extrairUrl(agendamento);

        if (!tipoComprovante.urlValida(caminho)) {
            return null;
        }

        return new CaminhoComprovante(agendamentoId, tipo, caminho);
    }

    /**
     * Record que encapsula o resultado da resolucao de comprovante.
     */
    public record CaminhoComprovante(UUID agendamentoId, String tipo, String caminho) {
    }
}
