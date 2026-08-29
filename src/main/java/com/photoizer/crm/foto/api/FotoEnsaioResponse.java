package com.photoizer.crm.foto.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO de resposta paraFotoEnsaio.
 * Mapeamento feito via FotoMapper (MapStruct) — não usar static of().
 */
public record FotoEnsaioResponse(
    UUID id,
    UUID agendamentoId,
    String fileName,
    String originalUrl,
    String watermarkedUrl,
    String thumbUrl,
    int ordem,
    String status,
    boolean selecionadaPacote,
    boolean downloadada,
    UUID compraExtraId,
    LocalDateTime createdAt,
    String titulo,
    String descricao,
    List<String> tags,
    String categoria,
    LocalDate dataSessao,
    String metadataExif,
    boolean destaque,
    UUID fotoEdicaoId,
    boolean visivel
) {}
