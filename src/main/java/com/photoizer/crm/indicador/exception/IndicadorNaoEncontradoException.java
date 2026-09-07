package com.photoizer.crm.indicador.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;

import java.util.UUID;

/**
 * Exceção de domínio lançada quando um indicador não é encontrado.
 * Mapeada para HTTP 404 NOT_FOUND pelo GlobalExceptionHandler.
 */
public class IndicadorNaoEncontradoException extends NotFoundException {

    public IndicadorNaoEncontradoException(UUID id) {
        super(ErrorCode.INDICADOR_NAO_ENCONTRADO, "Indicador não encontrado: " + id);
    }

    public IndicadorNaoEncontradoException(String mensagem) {
        super(ErrorCode.INDICADOR_NAO_ENCONTRADO, mensagem);
    }
}
