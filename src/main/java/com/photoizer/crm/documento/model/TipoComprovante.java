package com.photoizer.crm.documento.model;

/**
 * PATTERN: Enum Type Safety
 * Substitui magic strings "entrada"/"final" por tipo seguro com compile-time checking.
 * Centraliza a lógica de resolução do campo de comprovante no Agendamento.
 */
public enum TipoComprovante {

    ENTRADA("entrada", "Comprovante de entrada"),
    FINAL("final", "Comprovante final");

    private final String valor;
    private final String descricao;

    TipoComprovante(String valor, String descricao) {
        this.valor = valor;
        this.descricao = descricao;
    }

    public String getValor() {
        return valor;
    }

    public String getDescricao() {
        return descricao;
    }

    public String extrairUrl(com.photoizer.crm.agenda.model.Agendamento agendamento) {
        return switch (this) {
            case ENTRADA -> agendamento.getUrlComprovanteEntrada();
            case FINAL -> agendamento.getUrlComprovanteFinal();
        };
    }

    public boolean urlValida(String url) {
        return url != null && !url.isBlank();
    }

    public static TipoComprovante fromValor(String valor) {
        for (var tipo : values()) {
            if (tipo.valor.equalsIgnoreCase(valor)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException(
            "Tipo de comprovante inválido: '" + valor + "'. Valores aceitos: entrada, final.");
    }
}
