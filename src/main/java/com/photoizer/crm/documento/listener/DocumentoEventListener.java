package com.photoizer.crm.documento.listener;

import com.photoizer.crm.agenda.event.AgendamentoConfirmadoEvent;
import com.photoizer.crm.documento.service.DocumentoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * PATTERN: Event-Driven Decoupling
 * Usa @EventListener padrão (phase = DEFAULT) pois o método gerarContrato()
 * publica seu próprio ContratoGeradoEvent — se usássemos AFTER_COMMIT aqui,
 * a publicação do evento interno poderia não ser capturada corretamente.
 *
 * O DocumentoService.gerarContrato() é quem publica ContratoGeradoEvent
 * após sucesso na geração do PDF, mantendo o flag contratoGerado
 * sob domínio do módulo 'agenda'.
 */
@Component
public class DocumentoEventListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentoEventListener.class);

    private final DocumentoService documentoService;

    public DocumentoEventListener(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }

    @EventListener
    public void handleAgendamentoConfirmado(AgendamentoConfirmadoEvent event) {
        log.info("Gerando contrato automaticamente para agendamento confirmado {}", event.agendamentoId());
        try {
            documentoService.gerarContrato(event.agendamentoId());
        } catch (Exception e) {
            log.error("Falha ao gerar contrato para agendamento {}: {}",
                event.agendamentoId(), e.getMessage(), e);
        }
    }
}
