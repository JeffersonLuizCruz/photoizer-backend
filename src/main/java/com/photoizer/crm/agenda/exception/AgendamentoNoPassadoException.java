package com.photoizer.crm.agenda.exception;

public class AgendamentoNoPassadoException extends RuntimeException {

    public AgendamentoNoPassadoException() {
        super("Não é permitido agendar no passado");
    }
}
