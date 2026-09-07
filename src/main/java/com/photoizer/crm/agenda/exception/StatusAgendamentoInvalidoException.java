package com.photoizer.crm.agenda.exception;

import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.ErrorCode;

public class StatusAgendamentoInvalidoException extends ConflictException {

    public StatusAgendamentoInvalidoException(StatusAgendamento atual, StatusAgendamento proximo) {
        super(ErrorCode.STATUS_AGENDAMENTO_INVALIDO, "Transição de status inválida: " + atual + " → " + proximo);
    }
}
