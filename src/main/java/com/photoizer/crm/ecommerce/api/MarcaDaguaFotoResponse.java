package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.foto.model.FotoEnsaio;

import java.util.UUID;

public record MarcaDaguaFotoResponse(
    UUID fotoId,
    UUID agendamentoId
) {

    public static MarcaDaguaFotoResponse of(FotoEnsaio foto) {
        return new MarcaDaguaFotoResponse(foto.getId(), foto.getAgendamentoId());
    }
}
