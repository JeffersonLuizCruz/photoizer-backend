package com.photoizer.crm.agenda.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

public class EnsaioNaoFinalizadoException extends UnprocessableException {

    public EnsaioNaoFinalizadoException() {
        super(ErrorCode.ENSAIO_NAO_FINALIZADO, "Ensaio não finalizado. Para finalizar o ensaio é necessário registrar o comprovante de pagamento dos 70% restantes.");
    }

    public EnsaioNaoFinalizadoException(String message) {
        super(ErrorCode.ENSAIO_NAO_FINALIZADO, message);
    }
}
