package com.photoizer.crm.foto.exception;

import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.ErrorCode;

/**
 * Impede a renumeracao da galeria depois que os numeros ja podem ter sido
 * vistos pelo cliente, baixados, comprados ou vinculados a uma selecao.
 */
public class RenumeracaoNaoPermitidaException extends ConflictException {

    public RenumeracaoNaoPermitidaException(int quantidade) {
        super(ErrorCode.RENUMERACAO_NAO_PERMITIDA,
            quantidade + " foto(s) impedem a renumeracao por ja estarem selecionadas, "
                + "baixadas, compradas ou vinculadas a uma compra");
    }
}
