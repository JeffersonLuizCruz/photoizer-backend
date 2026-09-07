package com.photoizer.crm.indicador.exception;

import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.ErrorCode;

/**
 * Exceção de domínio lançada ao tentar criar um indicador com nome+telefone já existente.
 * Mapeada para HTTP 409 CONFLICT pelo GlobalExceptionHandler.
 */
public class IndicadorDuplicadoException extends ConflictException {

    public IndicadorDuplicadoException(String nome, String telefone) {
        super(ErrorCode.INDICADOR_DUPLICADO, "Indicador já cadastrado com nome '" + nome + "' e telefone '" + telefone + "'");
    }
}
