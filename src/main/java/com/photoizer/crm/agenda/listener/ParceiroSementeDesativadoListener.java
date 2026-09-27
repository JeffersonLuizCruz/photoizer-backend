package com.photoizer.crm.agenda.listener;

import com.photoizer.crm.agenda.service.AgendamentoFotografoService;
import com.photoizer.crm.auth.event.ParceiroSementeDesativadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * PATTERN: Event Listener (Modulith)
 *
 * Ao desativar um parceiro semeado legado (módulo auth), cancela os repasses
 * ainda pendentes desse parceiro — mantendo o auth sem acesso ao módulo agenda.
 *
 * Sem @Transactional no handler de propósito: cada cancelamento usa a transação
 * do próprio AgendamentoFotografoService, evitando rollback-only ao capturar falhas.
 */
@Component
public class ParceiroSementeDesativadoListener {

    private static final Logger log = LoggerFactory.getLogger(ParceiroSementeDesativadoListener.class);

    private final AgendamentoFotografoService agendamentoFotografoService;

    public ParceiroSementeDesativadoListener(AgendamentoFotografoService agendamentoFotografoService) {
        this.agendamentoFotografoService = agendamentoFotografoService;
    }

    @EventListener
    public void handleParceiroSementeDesativado(ParceiroSementeDesativadoEvent event) {
        try {
            var pendentes = agendamentoFotografoService.listarPendentesPorFotografo(event.userId());
            for (var link : pendentes) {
                try {
                    agendamentoFotografoService.cancelarRepasse(
                        link.getAgendamento().getId(), event.userId());
                    log.info("Repasse pendente cancelado do parceiro desativado {} (agendamento {})",
                        event.userId(), link.getAgendamento().getId());
                } catch (RuntimeException e) {
                    log.warn("Não foi possível cancelar repasse do parceiro desativado {} (agendamento {}): {}",
                        event.userId(), link.getAgendamento().getId(), e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            log.warn("Falha ao cancelar repasses do parceiro desativado {}: {}", event.userId(), e.getMessage());
        }
    }
}
