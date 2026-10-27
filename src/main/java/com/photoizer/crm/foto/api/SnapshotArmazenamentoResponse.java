package com.photoizer.crm.foto.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Um dia do histórico de consumo de armazenamento. */
public record SnapshotArmazenamentoResponse(
    LocalDate data,
    long bytesFotos,
    long bytesDerivados,
    long bytesEditadas,
    long bytesDocumentos,
    long bytesPropostas,
    long bytesOutros,
    long bytesTotal,
    long volumeTotal,
    long volumeLivre,
    int totalFotos,
    int totalGalerias,
    int galeriasArquivadas,
    int fotosArquivadas,
    long bytesLiberadosNoDia,
    LocalDateTime coletadoEm
) {}
