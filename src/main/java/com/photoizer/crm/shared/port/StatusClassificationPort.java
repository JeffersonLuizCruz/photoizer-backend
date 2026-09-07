package com.photoizer.crm.shared.port;

import com.photoizer.crm.agenda.model.StatusAgendamento;

import java.util.Set;

/**
 * PATTERN: Port (Hexagonal Architecture / Ports & Adapters)
 *
 * Porta de classificação de status de agendamentos.
 * Lógica pura de negócio — sem I/O, sem dependência de repositório.
 *
 * Motivo: O FinanceCalculator original misturava classificação de status
 * (pura), cálculo de deslocamento (pura) e agregação de repasses (I/O).
 * A separação em portas permite que shared defina abstrações sem
 * depender de módulos de negócio, invertendo o ciclo de dependências.
 *
 * Adaptador: StatusClassificationAdapter (permanece em shared/service/,
 * pois é lógica pura sem dependência de repositório).
 */
public interface StatusClassificationPort {

    /** Status que devem ser excluídos de cálculos financeiros (CANCELADO, NO_SHOW). */
    Set<StatusAgendamento> statusIgnorados();

    /** Status que representam trabalho finalizado (EM_EDICAO até FINALIZADO). */
    Set<StatusAgendamento> statusFinalizados();

    /** True se o status for CONFIRMADO, REALIZADO, AGUARDANDO_PAGAMENTO_FINAL ou qualquer finalizado. */
    boolean isConfirmadoOuFinalizado(StatusAgendamento status);
}
