package com.photoizer.crm.comissao.exception;

import java.util.UUID;

public class IndicacaoNaoEncontradaException extends RuntimeException {

    public IndicacaoNaoEncontradaException(UUID agendamentoId) {
        super("Indicação não encontrada para o agendamento: " + agendamentoId);
    }
}
