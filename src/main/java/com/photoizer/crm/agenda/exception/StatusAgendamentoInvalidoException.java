package com.photoizer.crm.agenda.exception;

import com.photoizer.crm.agenda.model.StatusAgendamento;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class StatusAgendamentoInvalidoException extends RuntimeException {
    public StatusAgendamentoInvalidoException(StatusAgendamento atual, StatusAgendamento proximo) {
        super("Transição de status inválida: " + atual + " → " + proximo);
    }
}
