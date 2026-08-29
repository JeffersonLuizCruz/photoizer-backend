package com.photoizer.crm.foto.event;

import java.util.UUID;

/**
 * Evento publicado pelo módulo edicao quando uma FotoEnsaio INEDITA deve ser removida
 * (foto desaprovada na revisão).
 *
 * PATTERN: Domain Events (Modulith)
 * Substitui exclusão direta do edicao em FotoEnsaio/FotoEnsaioRepository.
 */
public record FotoEdicaoRemovidaEvent(
    UUID fotoEdicaoId
) {}
