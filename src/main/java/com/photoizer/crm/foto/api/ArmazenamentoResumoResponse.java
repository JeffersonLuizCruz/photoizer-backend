package com.photoizer.crm.foto.api;

/**
 * Visão consolidada do disco de uploads para o painel de armazenamento.
 *
 * <p>{@code bytesTotal} considera todo o diretório de uploads; os campos
 * seguintes separam o consumo por tipo de arquivo. {@code volumeTotal} e
 * {@code volumeLivre} vêm do filesystem do volume que hospeda os uploads.
 */
public record ArmazenamentoResumoResponse(
    long bytesFotos,
    long bytesDerivados,
    long bytesEditadas,
    long bytesDocumentos,
    long bytesPropostas,
    long bytesOutros,
    long bytesTotal,
    long volumeTotal,
    long volumeLivre,
    double percentualUso,
    int totalGalerias,
    long totalFotos,
    int galeriasArquivadas,
    long bytesLiberados
) {}
