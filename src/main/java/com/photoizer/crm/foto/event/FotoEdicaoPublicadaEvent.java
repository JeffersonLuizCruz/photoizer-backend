package com.photoizer.crm.foto.event;

import java.util.UUID;

/**
 * Evento publicado pelo módulo edicao quando fotos editadas são criadas como FotoEnsaio.
 * O módulo foto consome este evento para criar FotoEnsaio sem escrita cross-module.
 *
 * PATTERN: Domain Events (Modulith)
 * Substitui escrita direta do edicao em FotoEnsaio/FotoEnsaioRepository.
 */
public record FotoEdicaoPublicadaEvent(
    UUID agendamentoId,
    UUID fotoEdicaoId,
    String fileName,
    String originalPath,
    UUID fotoId
) {}
