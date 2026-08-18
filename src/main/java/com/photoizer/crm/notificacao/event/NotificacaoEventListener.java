package com.photoizer.crm.notificacao.event;

import com.photoizer.crm.agenda.event.AgendamentoCriadoEvent;
import com.photoizer.crm.agenda.event.AgendamentoRealizadoEvent;
import com.photoizer.crm.agenda.event.PagamentoFinalRegistradoEvent;
import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.notificacao.model.TipoNotificacao;
import com.photoizer.crm.notificacao.service.NotificacaoService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
public class NotificacaoEventListener {

    private final NotificacaoService notificacaoService;
    private final AgendamentoRepository agendamentoRepository;
    private final AgendamentoFotografoRepository agendamentoFotografoRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    public NotificacaoEventListener(NotificacaoService notificacaoService,
                                    AgendamentoRepository agendamentoRepository,
                                    AgendamentoFotografoRepository agendamentoFotografoRepository) {
        this.notificacaoService = notificacaoService;
        this.agendamentoRepository = agendamentoRepository;
        this.agendamentoFotografoRepository = agendamentoFotografoRepository;
    }

    @EventListener
    public void onAgendamentoCriado(AgendamentoCriadoEvent event) {
        var agendamento = agendamentoRepository.findById(event.agendamentoId()).orElse(null);
        if (agendamento == null) return;
        var links = agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(event.agendamentoId());
        if (links.isEmpty()) return;

        var dataStr = agendamento.getDataHoraEnsaio().format(DATE_FMT);
        for (var link : links) {
            notificacaoService.criar(
                link.getFotografo().getId(),
                "Novo Ensaio Agendado",
                "Você tem um novo ensaio com " + agendamento.getCliente().getNome() + " em " + dataStr + ".",
                "/agenda/" + agendamento.getId(),
                TipoNotificacao.NOVO_ENSAIO
            );
        }
    }

    @EventListener
    public void onAgendamentoRealizado(AgendamentoRealizadoEvent event) {
        var links = agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(event.agendamentoId());
        if (links.isEmpty()) return;

        var agendamento = agendamentoRepository.findById(event.agendamentoId()).orElse(null);
        if (agendamento == null) return;

        for (var link : links) {
            notificacaoService.criar(
                link.getFotografo().getId(),
                "Ensaio Realizado",
                "O ensaio com " + agendamento.getCliente().getNome() + " foi realizado com sucesso.",
                "/agenda/" + agendamento.getId(),
                TipoNotificacao.ENSAIO_REALIZADO
            );
        }
    }

    @EventListener
    public void onPagamentoFinalRegistrado(PagamentoFinalRegistradoEvent event) {
        var links = agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(event.agendamentoId());
        if (links.isEmpty()) return;

        var agendamento = agendamentoRepository.findById(event.agendamentoId()).orElse(null);
        if (agendamento == null) return;

        for (var link : links) {
            notificacaoService.criar(
                link.getFotografo().getId(),
                "Pagamento Final Recebido",
                "O pagamento final do ensaio com " + agendamento.getCliente().getNome()
                    + " foi confirmado. Sua partilha já está disponível para consulta.",
                "/minhas-financas",
                TipoNotificacao.PAGAMENTO_FINAL
            );
        }
    }
}