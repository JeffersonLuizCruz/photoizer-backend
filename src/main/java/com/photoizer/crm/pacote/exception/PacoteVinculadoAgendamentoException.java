package com.photoizer.crm.pacote.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

import java.util.UUID;

public class PacoteVinculadoAgendamentoException extends UnprocessableException {

    public PacoteVinculadoAgendamentoException(UUID id, long totalAgendamentos) {
        super(ErrorCode.PACOTE_VINCULADO_A_AGENDAMENTOS,
            "Pacote não pode ser desativado pois possui " + totalAgendamentos
                + " agendamento(s) ativo(s) vinculado(s): " + id);
    }
}
