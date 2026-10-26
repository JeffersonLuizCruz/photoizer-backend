package com.photoizer.crm.ecommerce.event;

import java.util.List;
import java.util.UUID;

/** Publicado quando o staff libera a versao editada para o cliente. */
public record EdicaoPublicadaEvent(
    UUID agendamentoId,
    UUID selecaoId,
    List<UUID> fotoIds
) {}
