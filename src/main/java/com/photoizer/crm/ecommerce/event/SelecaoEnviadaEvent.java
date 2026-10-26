package com.photoizer.crm.ecommerce.event;

import java.util.List;
import java.util.UUID;

public record SelecaoEnviadaEvent(
    UUID agendamentoId,
    UUID selecaoId,
    List<UUID> fotoIds,
    int quantidadePacote,
    int quantidadeExtras,
    String clienteNome,
    UUID editorId,
    UUID fotografoId
) {}
