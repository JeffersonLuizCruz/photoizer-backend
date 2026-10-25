package com.photoizer.crm.foto.event;

import java.util.List;
import java.util.UUID;

/**
 * Publicado após a remoção de fotos para que o módulo ecommerce limpe
 * referências em carrinhos, favoritos e comentários.
 */
public record FotosRemovidasEvent(UUID agendamentoId, List<UUID> fotoIds) {}
