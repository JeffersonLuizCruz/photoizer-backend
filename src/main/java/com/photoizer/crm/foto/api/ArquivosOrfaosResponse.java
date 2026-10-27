package com.photoizer.crm.foto.api;

import java.util.List;

/**
 * Relatório de arquivos órfãos do diretório de uploads.
 *
 * <p>Arquivos com nome desconhecido são listados, mas nunca apagados. A lista
 * é limitada para não estourar a resposta; os totais consideram todos.
 */
public record ArquivosOrfaosResponse(
    int total,
    long bytes,
    int elegiveis,
    long bytesElegiveis,
    int desconhecidos,
    int listados,
    boolean truncado,
    List<ArquivoOrfaoResponse> arquivos
) {}
