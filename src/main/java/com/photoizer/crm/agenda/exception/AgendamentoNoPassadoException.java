package com.photoizer.crm.agenda.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

public class AgendamentoNoPassadoException extends UnprocessableException {

    public AgendamentoNoPassadoException() {
        super(ErrorCode.AGENDAMENTO_NO_PASSADO, "Não é permitido agendar no passado");
    }
}
