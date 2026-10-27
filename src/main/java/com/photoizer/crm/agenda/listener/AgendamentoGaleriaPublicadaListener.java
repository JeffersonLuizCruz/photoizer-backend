package com.photoizer.crm.agenda.listener;

import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.foto.event.FotosGaleriaPublicadasEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

/**
 * Renova a validade do link da galeria quando as fotos sao publicadas.
 *
 * <p>O token nasce junto com a proposta e pode vencer antes do ensaio
 * acontecer. A publicacao e o momento em que o link passa a ser util ao
 * cliente, entao a validade e estendida (sem rotacionar o token, para nao
 * quebrar links ja enviados).
 */
@Component
public class AgendamentoGaleriaPublicadaListener {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoGaleriaPublicadaListener.class);

    /** Janela de acesso a galeria depois da primeira publicacao. */
    private static final int DIAS_VALIDADE_APOS_PUBLICACAO = 180;

    private final AgendamentoRepository agendamentoRepository;

    public AgendamentoGaleriaPublicadaListener(AgendamentoRepository agendamentoRepository) {
        this.agendamentoRepository = agendamentoRepository;
    }

    @EventListener
    @Transactional
    public void handleFotosPublicadas(FotosGaleriaPublicadasEvent event) {
        agendamentoRepository.findById(event.agendamentoId()).ifPresent(agendamento -> {
            var novaExpiracao = event.publicadoEm()
                .toLocalDate()
                .plusDays(DIAS_VALIDADE_APOS_PUBLICACAO)
                .atTime(LocalTime.MAX);
            var expiracaoAnterior = agendamento.getTokenExpiracao();
            agendamento.estenderTokenGaleria(novaExpiracao);
            if (!java.util.Objects.equals(expiracaoAnterior, agendamento.getTokenExpiracao())) {
                agendamentoRepository.save(agendamento);
                log.atInfo()
                    .addKeyValue("event_action", "agendamento.token_galeria_estendido")
                    .addKeyValue("agendamento_id", event.agendamentoId())
                    .addKeyValue("token_expiracao", agendamento.getTokenExpiracao().toString())
                    .log("Validade do link da galeria estendida após a publicação das fotos");
            }
        });
    }
}
