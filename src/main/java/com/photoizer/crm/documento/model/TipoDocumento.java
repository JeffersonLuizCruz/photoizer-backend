package com.photoizer.crm.documento.model;

/**
 * PATTERN: Enum Type Safety
 *
 * Substitui strings magicas por tipo seguro com compile-time checking.
 * Usado como chave de resolucao de estrategias no DocumentoService.
 *
 * Nota: o termo de prestacao de servicos assinado passou a ser gerado pelo
 * modulo agenda (fluxo de proposta pública); o modulo documento agora gera
 * apenas recibos e serve comprovantes.
 */
public enum TipoDocumento {

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
            "Tipo de documento invalido: '" + valor + "'. Tipos disponiveis: recibo.");
    }
}
