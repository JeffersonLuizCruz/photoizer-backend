package com.photoizer.crm.edicao.listener;

import com.photoizer.crm.agenda.event.PagamentoFinalRegistradoEvent;
import com.photoizer.crm.edicao.model.Edicao;
import com.photoizer.crm.edicao.model.StatusEdicao;
import com.photoizer.crm.edicao.repository.EdicaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EdicaoListener {

    private static final Logger log = LoggerFactory.getLogger(EdicaoListener.class);

    private final EdicaoRepository edicaoRepository;

    public EdicaoListener(EdicaoRepository edicaoRepository) {
        this.edicaoRepository = edicaoRepository;
    }

    @EventListener
    @Transactional
    public void handlePagamentoFinal(PagamentoFinalRegistradoEvent event) {
        if (edicaoRepository.existsByAgendamentoId(event.agendamentoId())) return;

        edicaoRepository.save(Edicao.builder()
            .agendamentoId(event.agendamentoId())
            .status(StatusEdicao.AGUARDANDO_RAW)
            .build());

        log.info("Processo de edição criado automaticamente para agendamento {}", event.agendamentoId());
    }
}
