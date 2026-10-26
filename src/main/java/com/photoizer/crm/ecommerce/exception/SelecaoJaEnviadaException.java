package com.photoizer.crm.ecommerce.exception;

import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.ErrorCode;

public class SelecaoJaEnviadaException extends ConflictException {

    public SelecaoJaEnviadaException() {
        super(ErrorCode.SELECAO_JA_ENVIADA,
            "Já existe uma seleção ativa para esta galeria. Aguarde a edição ou solicite a reabertura.");
    }
}
