package com.photoizer.crm.ecommerce.model;

/**
 * Fase comercial da galeria. E ortogonal ao {@code StatusFoto}: o status da
 * foto continua governando o ciclo de compra, enquanto a fase governa o que o
 * cliente pode fazer na galeria.
 */
public enum FaseGaleria {
    SELECAO,
    SELECAO_ENVIADA,
    EM_EDICAO,
    COMERCIALIZACAO;

    public boolean permiteSelecionar() {
        return this == SELECAO;
    }

    public boolean permiteComercializar() {
        return this == COMERCIALIZACAO;
    }
}
