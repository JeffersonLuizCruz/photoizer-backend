package com.photoizer.crm.foto.api;

import java.util.List;

/**
 * Série diária de consumo. Dias sem coleta simplesmente não aparecem; o
 * cliente decide como exibir a lacuna.
 */
public record HistoricoArmazenamentoResponse(
    int dias,
    List<SnapshotArmazenamentoResponse> snapshots
) {}
