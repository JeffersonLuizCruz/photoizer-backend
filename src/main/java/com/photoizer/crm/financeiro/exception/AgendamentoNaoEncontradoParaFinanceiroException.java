package com.photoizer.crm.financeiro.exception;

import java.util.UUID;

public class AgendamentoNaoEncontradoParaFinanceiroException extends RuntimeException {
    public AgendamentoNaoEncontradoParaFinanceiroException(UUID id) {
        super("Agendamento não encontrado para operação financeira: " + id);
    }
}
