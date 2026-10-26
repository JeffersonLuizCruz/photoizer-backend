package com.photoizer.crm.ecommerce.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

public class FotosEditadasIncompletasException extends UnprocessableException {

    public FotosEditadasIncompletasException(long pendentes) {
        super(ErrorCode.FOTOS_EDITADAS_INCOMPLETAS,
            pendentes + " foto(s) da seleção ainda não possuem versão editada");
    }
}
