package com.photoizer.crm.shared.service;

import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.shared.port.StatusClassificationPort;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * PATTERN: Adapter (Hexagonal Architecture / Ports & Adapters)
 *
 * Implementação de StatusClassificationPort com lógica pura de classificação.
 * Permanece em shared/service/ pois não depende de I/O nem de repositórios.
 *
 * Classifica status de agendamento em categorias de negócio:
 * - Ignorados: CANCELADO, NO_SHOW (excluídos de cálculos financeiros)
 * - Finalizados: EM_EDICAO até FINALIZADO (trabalho concluído)
 * - Confirmados ou finalizados: CONFIRMADO até FINALIZADO (recipientes de receita)
 */
@Component
public class StatusClassificationAdapter implements StatusClassificationPort {

    /**
     * Status que NÃO representam um ensaio efetivo na agenda:
     * cancelados, no-show e toda a fase de proposta/pré-reserva
     * (que só passa a ocupar a agenda após a aprovação).
     */
    private static final Set<StatusAgendamento> STATUS_IGNORADOS = Set.of(
        StatusAgendamento.CANCELADO, StatusAgendamento.NO_SHOW,
        StatusAgendamento.PRE_RESERVA, StatusAgendamento.AGUARDANDO_APROVACAO,
        StatusAgendamento.PAGAMENTO_CONFIRMADO
    );

    private static final Set<StatusAgendamento> STATUS_FINALIZADOS = Set.of(
        StatusAgendamento.EM_EDICAO,
        StatusAgendamento.FINALIZADO
    );

    private static final Set<StatusAgendamento> STATUS_CONFIRMADOS_OU_FINALIZADOS = Set.of(
        StatusAgendamento.CONFIRMADO,
        StatusAgendamento.REALIZADO,
        StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL,
        StatusAgendamento.EM_EDICAO,
        StatusAgendamento.FINALIZADO
    );

    @Override
    public Set<StatusAgendamento> statusIgnorados() {
        return STATUS_IGNORADOS;
    }

    @Override
    public Set<StatusAgendamento> statusFinalizados() {
        return STATUS_FINALIZADOS;
    }

    @Override
    public boolean isConfirmadoOuFinalizado(StatusAgendamento status) {
        return STATUS_CONFIRMADOS_OU_FINALIZADOS.contains(status);
    }
}
