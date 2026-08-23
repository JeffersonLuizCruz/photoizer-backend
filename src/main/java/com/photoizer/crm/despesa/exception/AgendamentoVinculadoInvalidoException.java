package com.photoizer.crm.despesa.exception;

import java.util.UUID;

public class AgendamentoVinculadoInvalidoException extends RuntimeException {

    public AgendamentoVinculadoInvalidoException(UUID agendamentoId) {
        super("Trabalho vinculado não encontrado: " + agendamentoId);
    }
}
