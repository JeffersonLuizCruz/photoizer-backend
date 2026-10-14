package com.photoizer.crm.agenda.exception;

import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

/**
 * Lançada quando se tenta remover uma proposta que já saiu do funil de
 * proposta/pré-reserva (ex.: confirmada, realizada ou cancelada). A remoção é um
 * cancelamento auditável e só se aplica a agendamentos que não ocupam a agenda.
 */
public class PropostaNaoRemovivelException extends UnprocessableException {

    public PropostaNaoRemovivelException(StatusAgendamento statusAtual) {
        super(ErrorCode.PROPOSTA_ESTADO_INVALIDO,
            "Somente propostas em análise (pré-reserva) podem ser removidas. Status atual: "
                + statusAtual);
    }
}
