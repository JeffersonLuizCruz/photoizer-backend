package com.photoizer.crm.foto.api;

import com.photoizer.crm.foto.model.MotivoExpurgoBloqueado;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Consumo de disco de uma galeria.
 *
 * <p>{@code arquivada} é derivada: só é verdadeira quando a galeria tem fotos e
 * todas já tiveram os arquivos expurgados. {@code expurgavel} resume as regras
 * (ensaio finalizado, sem pendências e com fotos ainda não arquivadas).
 */
public record GaleriaArmazenamentoResponse(
    UUID agendamentoId,
    String clienteNome,
    LocalDateTime dataHoraEnsaio,
    String status,
    int quantidadeFotos,
    int quantidadeArquivadas,
    long bytes,
    long bytesFotos,
    long bytesDocumentos,
    LocalDateTime ultimoDownload,
    boolean arquivada,
    LocalDateTime arquivadaEm,
    long bytesLiberados,
    boolean expurgavel,
    MotivoExpurgoBloqueado motivoBloqueio
) {}
