package com.photoizer.crm.despesa.exception;

import java.util.UUID;

public class DespesaRecorrenteNaoPagaException extends RuntimeException {

    public DespesaRecorrenteNaoPagaException(UUID id) {
        super("Despesas recorrentes não podem ser marcadas como pagas diretamente. " +
              "Marque a ocorrência gerada. ID da despesa recorrente: " + id);
    }
}
