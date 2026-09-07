package com.photoizer.crm.despesa.exception;

import java.util.UUID;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

public class AgendamentoVinculadoInvalidoException extends UnprocessableException {

    public AgendamentoVinculadoInvalidoException(UUID agendamentoId) {
        super(ErrorCode.AGENDAMENTO_VINCULADO_INVALIDO, "Trabalho vinculado não encontrado: " + agendamentoId);
    }
}
