package com.photoizer.crm.foto.api;

/**
 * Resultado do expurgo de uma galeria.
 *
 * @param fotos          quantidade de fotos cujos arquivos foram removidos
 * @param bytesLiberados redução real do diretório da galeria
 */
public record ExpurgarGaleriaResponse(int fotos, long bytesLiberados) {}
