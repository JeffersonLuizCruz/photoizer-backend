package com.photoizer.crm.despesa.exception;

import java.util.UUID;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

public class DespesaRecorrenteNaoPagaException extends UnprocessableException {

    public DespesaRecorrenteNaoPagaException(UUID id) {
        super(ErrorCode.DESPESA_RECORRENTE_NAO_PAGA, "Despesas recorrentes não podem ser marcadas como pagas diretamente. " +
              "Marque a ocorrência gerada. ID da despesa recorrente: " + id);
    }
}
