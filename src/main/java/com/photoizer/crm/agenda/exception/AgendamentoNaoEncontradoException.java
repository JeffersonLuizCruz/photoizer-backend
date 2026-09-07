package com.photoizer.crm.agenda.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;

import java.util.UUID;

public class AgendamentoNaoEncontradoException extends NotFoundException {

    public AgendamentoNaoEncontradoException(UUID id) {
        super(ErrorCode.AGENDAMENTO_NAO_ENCONTRADO, "Agendamento não encontrado: " + id);
    }
}
