package com.photoizer.crm.edicao.event;

import java.util.UUID;

public record FotosPublicadasEvent(
    UUID agendamentoId,
    int totalFotos,
    TipoPublicacao tipo
) {
    public enum TipoPublicacao {
        ECOMMERCE,
        LOJA
    }
}
