package com.photoizer.crm.agenda.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.GoneException;

public class PropostaTokenExpiradoException extends GoneException {

    public PropostaTokenExpiradoException() {
        super(ErrorCode.PROPOSTA_TOKEN_EXPIRADO, "O link da proposta expirou. Solicite um novo link ao fotógrafo.");
    }
}
