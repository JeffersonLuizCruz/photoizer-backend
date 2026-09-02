package com.photoizer.crm.indicador.exception;

/**
 * Exceção de domínio lançada ao tentar criar um indicador com nome+telefone já existente.
 * Mapeada para HTTP 409 CONFLICT pelo GlobalExceptionHandler.
 */
public class IndicadorDuplicadoException extends RuntimeException {

    public IndicadorDuplicadoException(String nome, String telefone) {
        super("Indicador já cadastrado com nome '" + nome + "' e telefone '" + telefone + "'");
    }
}
