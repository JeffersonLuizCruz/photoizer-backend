package com.photoizer.crm.ecommerce.model;

/**
 * Modo de operacao da galeria.
 *
 * <ul>
 *   <li>{@link #DIRETO}: fluxo legado, sem rodada obrigatoria de edicao.</li>
 *   <li>{@link #COM_EDICAO}: cliente envia selecao, staff edita e publica a
 *       versao editada antes da comercializacao.</li>
 * </ul>
 */
public enum ModoGaleria {
    DIRETO,
    COM_EDICAO
}
