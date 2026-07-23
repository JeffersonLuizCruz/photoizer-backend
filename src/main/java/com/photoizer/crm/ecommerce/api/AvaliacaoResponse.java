package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.Avaliacao;

import java.time.LocalDateTime;
import java.util.UUID;

public record AvaliacaoResponse(
    UUID id,
    UUID clienteId,
    UUID agendamentoId,
    UUID pacoteId,
    int pontuacao,
    String comentario,
    boolean depoimento,
    boolean aprovado,
    LocalDateTime createdAt
) {
    public static AvaliacaoResponse of(Avaliacao a) {
        return new AvaliacaoResponse(
            a.getId(), a.getClienteId(), a.getAgendamentoId(), a.getPacoteId(),
            a.getPontuacao(), a.getComentario(), a.isDepoimento(), a.isAprovado(),
            a.getCreatedAt()
        );
    }
}
