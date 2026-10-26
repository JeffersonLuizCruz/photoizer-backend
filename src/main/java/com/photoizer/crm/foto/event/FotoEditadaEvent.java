package com.photoizer.crm.foto.event;

import java.util.UUID;

/** Publicado quando uma versao editada e enviada para uma foto existente. */
public record FotoEditadaEvent(
    UUID agendamentoId,
    UUID fotoId
) {}
