package com.photoizer.crm.cliente.exception;

/**
 * Exceção de domínio para cliente duplicado.
 * Substitui IllegalArgumentException genérica.
 * 
 * Padrão Domain Exception -异常 de domínio específicas
 * com informações úteis para troubleshooting.
 */
public class ClienteDuplicadoException extends RuntimeException {

    private final String campo;
    private final String valor;

    public ClienteDuplicadoException(String campo, String valor) {
        super("Cliente já cadastrado com " + campo + ": " + valor);
        this.campo = campo;
        this.valor = valor;
    }

    public ClienteDuplicadoException(String mensagem) {
        super(mensagem);
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
