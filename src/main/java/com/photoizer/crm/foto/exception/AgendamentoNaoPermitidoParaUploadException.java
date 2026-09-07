package com.photoizer.crm.foto.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

public class AgendamentoNaoPermitidoParaUploadException extends UnprocessableException {
    public AgendamentoNaoPermitidoParaUploadException() {
        super(ErrorCode.AGENDAMENTO_NAO_PERMITIDO_PARA_UPLOAD, "Agendamento não está em status permitido para upload de fotos");
    }
}
