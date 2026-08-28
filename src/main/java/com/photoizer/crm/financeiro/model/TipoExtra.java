package com.photoizer.crm.financeiro.model;

/**
 * Tipo de extra vendido: Foto ou Vídeo.
 *
 * Pattern: Enum — substitui a duplicação de entidades FotoExtra/VideoExtra
 * por uma única entidade ExtraServico com discriminador de tipo.
 */
public enum TipoExtra {
    FOTO,
    VIDEO
}
