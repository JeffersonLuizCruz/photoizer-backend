package com.photoizer.crm.financeiro.model;

/**
 * Tipo de serviço prestado.
 *
 * Pattern: Enum com comportamento — label() elimina duplicação de
 * switch/if-else em FinanceiroService e FinanceiroDashboardService.
 */
public enum TipoServico {
    ENSAIO("Ensaio"),
    CASAMENTO("Casamento"),
    EVENTO("Evento"),
    PRODUTO("Produto"),
    OUTRO("Outro");

    private final String label;

    TipoServico(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
