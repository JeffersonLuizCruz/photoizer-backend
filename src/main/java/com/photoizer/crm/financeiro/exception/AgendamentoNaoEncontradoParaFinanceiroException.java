package com.photoizer.crm.financeiro.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;

import java.util.UUID;

public class AgendamentoNaoEncontradoParaFinanceiroException extends NotFoundException {
    public AgendamentoNaoEncontradoParaFinanceiroException(UUID id) {
        super(ErrorCode.AGENDAMENTO_NAO_ENCONTRADO_PARA_FINANCEIRO, "Agendamento não encontrado para operação financeira: " + id);
    }
}
