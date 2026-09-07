package com.photoizer.crm.agenda.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

public class ComprovanteObrigatorioException extends UnprocessableException {

    public ComprovanteObrigatorioException() {
        super(ErrorCode.COMPROVANTE_OBRIGATORIO, "Comprovante de pagamento é obrigatório para finalizar o ensaio");
    }
}
