package com.photoizer.crm.notificacao.event;

import com.photoizer.crm.agenda.event.AgendamentoCriadoEvent;
import com.photoizer.crm.agenda.event.AgendamentoRealizadoEvent;
import com.photoizer.crm.agenda.event.PagamentoFinalRegistradoEvent;
import com.photoizer.crm.notificacao.model.TipoNotificacao;
import com.photoizer.crm.notificacao.service.NotificacaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * PATTERN: Event Listener (Modulith) — refatorado (P1).
 *
 * Antes, este listener injetava AgendamentoRepository e AgendamentoFotografoRepository
 * do módulo agenda, violando fronteiras entre módulos e correndo risco de
 * LazyInitializationException (acesso a getCliente().getNome() fora de transação).
 *
 * Agora, os eventos de agenda são enriquecidos (Event Enrichment) com clienteNome
 * e fotografoIds resolvidos pelo publisher, eliminando a dependência de repositórios
 * alheios. O método helper notificarFotografos() elimina a duplicação dos 3 handlers.
 */
@Component
public class NotificacaoEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoEventListener.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final NotificacaoService notificacaoService;

    public NotificacaoEventListener(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    @EventListener
    @Transactional
    public void onAgendamentoCriado(AgendamentoCriadoEvent event) {
        if (event.fotografoIds().isEmpty()) return;

        var dataStr = event.dataHoraEnsaio().format(DATE_FMT);
        notificarFotografos(
            event.fotografoIds(),
            "Novo Ensaio Agendado",
            "Você tem um novo ensaio com " + event.clienteNome() + " em " + dataStr + ".",
            "/agenda/" + event.agendamentoId(),
            TipoNotificacao.NOVO_ENSAIO
        );
    }

    @EventListener
    @Transactional
    public void onAgendamentoRealizado(AgendamentoRealizadoEvent event) {
        if (event.fotografoIds().isEmpty()) return;

        notificarFotografos(
            event.fotografoIds(),
            "Ensaio Realizado",
            "O ensaio com " + event.clienteNome() + " foi realizado com sucesso.",
            "/agenda/" + event.agendamentoId(),
            TipoNotificacao.ENSAIO_REALIZADO
        );
    }

    @EventListener
    @Transactional
    public void onPagamentoFinalRegistrado(PagamentoFinalRegistradoEvent event) {
        if (event.fotografoIds().isEmpty()) return;

        notificarFotografos(
            event.fotografoIds(),
            "Pagamento Final Recebido",
            "O pagamento final do ensaio com " + event.clienteNome()
                + " foi confirmado. Sua partilha já está disponível para consulta.",
            "/minhas-financas",
            TipoNotificacao.PAGAMENTO_FINAL
        );
    }

    /**
     * Cria notificações para todos os fotógrafos de forma atômica.
     * PATTERN: Transactional script — se qualquer notificação falhar, todas as anteriores
     * são revertidas via rollback, evitando notificações parciais.
     */
    private void notificarFotografos(List<UUID> fotografoIds, String titulo,
                                     String mensagem, String link, TipoNotificacao tipo) {
        for (UUID fotografoId : fotografoIds) {
            notificacaoService.criar(fotografoId, titulo, mensagem, link, tipo);
        }
    }
}
