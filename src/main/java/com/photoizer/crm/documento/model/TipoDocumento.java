package com.photoizer.crm.documento.model;

/**
 * PATTERN: Enum Type Safety
 *
 * Substitui strings magicas ("contrato", "recibo") por tipo seguro com compile-time checking.
 * Usado como chave de resolucao de estrategias no DocumentoService.
 *
 * Motivo: Antes, a resolucao de estrategia usava String como chave, o que e propenso
 * a erros de typo em runtime. Enum garante que apenas valores validos sejam usados.
 */
public enum TipoDocumento {

    CONTRATO("contrato"),
    RECIBO("recibo");

    private final String valor;

    TipoDocumento(String valor) {
        this.valor = valor;
    }

    public String getValor() {
        return valor;
    }

    public static TipoDocumento fromValor(String valor) {
        for (var tipo : values()) {
            if (tipo.valor.equalsIgnoreCase(valor)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException(
            "Tipo de documento invalido: '" + valor + "'. Tipos disponiveis: contrato, recibo.");
    }
}
