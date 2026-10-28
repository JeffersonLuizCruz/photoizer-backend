package com.photoizer.crm.foto.api;

/**
 * Resultado da aplicação da qualidade de prévia em uma galeria.
 *
 * <p>{@code geradas} são fotos que passaram a usar um cache novo,
 * {@code reaproveitadas} já estavam na qualidade atual e {@code ignoradas} são
 * fotos não publicadas, invisíveis ou arquivadas.
 */
public record AplicarQualidadePreviewResponse(
    String qualidade,
    int total,
    int geradas,
    int reaproveitadas,
    int falhas,
    int ignoradas
) {}
