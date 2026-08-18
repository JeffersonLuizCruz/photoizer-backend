package com.photoizer.crm.notificacao.api;

import com.photoizer.crm.notificacao.model.Notificacao;
import com.photoizer.crm.notificacao.model.TipoNotificacao;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificacaoResponse(
    UUID id,
    UUID userId,
    String titulo,
    String mensagem,
    String link,
    TipoNotificacao tipo,
    boolean lida,
    LocalDateTime createdAt
) {
    public static NotificacaoResponse of(Notificacao n) {
        return new NotificacaoResponse(
            n.getId(), n.getUserId(), n.getTitulo(), n.getMensagem(),
            n.getLink(), n.getTipo(), n.isLida(), n.getCreatedAt()
        );
    }
}