package com.photoizer.crm.foto.api;

/**
 * Resultado do pré-aquecimento das prévias protegidas de uma galeria.
 *
 * <p>{@code geradas} são fotos que passaram a usar um cache novo,
 * {@code reaproveitadas} já estavam na versão atual e {@code ignoradas} são
 * fotos não publicadas, invisíveis ou arquivadas.
 */
public record AquecerPreviasResponse(
    int total,
    int geradas,
    int reaproveitadas,
    int falhas,
    int ignoradas
) {}
