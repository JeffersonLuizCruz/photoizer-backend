package com.photoizer.crm.agenda.exception;

import java.util.UUID;

public class AgendamentoNaoEncontradoException extends RuntimeException {

    public AgendamentoNaoEncontradoException(UUID id) {
        super("Agendamento não encontrado: " + id);
    }
}
