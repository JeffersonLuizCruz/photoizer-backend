package com.photoizer.crm.cliente.exception;

import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.ErrorCode;

/**
 * Exceção de domínio para cliente duplicado.
 * Substitui IllegalArgumentException genérica.
 * 
 * Padrão Domain Exception -异常 de domínio específicas
 * com informações úteis para troubleshooting.
 */
public class ClienteDuplicadoException extends ConflictException {

    private final String campo;
    private final String valor;

    public ClienteDuplicadoException(String campo, String valor) {
        super(ErrorCode.CLIENTE_DUPLICADO, "Cliente já cadastrado com " + campo + ": " + valor);
        this.campo = campo;
        this.valor = valor;
    }

    public ClienteDuplicadoException(String mensagem) {
        super(ErrorCode.CLIENTE_DUPLICADO, mensagem);
        this.campo = null;
        this.valor = null;
    }

    public String getCampo() {
        return campo;
    }

    public String getValor() {
        return valor;
    }
}
