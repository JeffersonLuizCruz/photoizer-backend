package com.photoizer.crm.notificacao.scheduler;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.notificacao.service.NotificacaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Transactional
public class LembreteAgendamentoJob {

    private static final Logger log = LoggerFactory.getLogger(LembreteAgendamentoJob.class);
    private static final List<StatusAgendamento> STATUS_IGNORADOS = List.of(
        StatusAgendamento.CANCELADO, StatusAgendamento.NO_SHOW
    );

    private final AgendamentoRepository agendamentoRepository;
    private final UserRepository userRepository;
    private final NotificacaoService notificacaoService;
    private final ConfiguracaoService configuracaoService;

    public LembreteAgendamentoJob(AgendamentoRepository agendamentoRepository,
                                   UserRepository userRepository,
                                   NotificacaoService notificacaoService,
                                   ConfiguracaoService configuracaoService) {
        this.agendamentoRepository = agendamentoRepository;
        this.userRepository = userRepository;
        this.notificacaoService = notificacaoService;
        this.configuracaoService = configuracaoService;
    }

    @Scheduled(cron = "0 0 6 * * *")
    public void enviarLembretesDiarios() {
        var ativo = configuracaoService.getValorDecimal("notificarAutomaticamente", BigDecimal.ONE)
            .compareTo(BigDecimal.ONE) == 0;
        if (!ativo) {
            log.info("Notificacao automatica desativada. Pulando job.");
            return;
        }

        var amanha = LocalDate.now().plusDays(1);
        var inicio = amanha.atStartOfDay();
        var fim = amanha.atTime(LocalTime.MAX);

        var agendamentos = agendamentoRepository.findByDataBetween(inicio, fim, STATUS_IGNORADOS);
        if (agendamentos.isEmpty()) {
            log.info("Nenhum agendamento para amanha ({}).", amanha);
            return;
        }

        var porFotografo = agendamentos.stream()
            .collect(Collectors.groupingBy(a -> a.getPacote().getFotografo().getId()));

        var fotografoNomes = userRepository.findAll().stream()
            .collect(Collectors.toMap(u -> u.getId(), u -> u.getNome()));

        for (var entry : porFotografo.entrySet()) {
            var fotografoId = entry.getKey();
            var lista = entry.getValue();
            var nomeFotografo = fotografoNomes.getOrDefault(fotografoId, "Fotógrafo");

            var sb = new StringBuilder();
            sb.append("Você tem **").append(lista.size())
              .append(" agendamento").append(lista.size() > 1 ? "s" : "")
              .append("** para amanhã (").append(amanha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
              .append("):\n\n");

            for (var a : lista) {
                var hora = a.getDataHoraEnsaio().format(DateTimeFormatter.ofPattern("HH:mm"));
                sb.append("• ").append(hora).append(" — ").append(a.getCliente().getNome());
                if (a.getLocalEnsaio() != null && !a.getLocalEnsaio().isBlank()) {
                    sb.append(" (").append(a.getLocalEnsaio()).append(")");
                }
                sb.append("\n");
            }

            notificacaoService.criar(
                fotografoId,
                "📸 Agenda de amanhã",
                sb.toString(),
                "/agenda?data=" + amanha
            );

            log.info("Lembrete enviado para fotografo {} ({}) com {} agendamentos",
                nomeFotografo, fotografoId, lista.size());
        }
    }

}
