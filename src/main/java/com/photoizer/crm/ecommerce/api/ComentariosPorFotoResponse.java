package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.foto.api.FotoEnsaioResponse;

import java.util.List;

public record ComentariosPorFotoResponse(
    FotoEnsaioResponse foto,
    List<ComentarioResponse> comentarios,
    long naoLidas
) {}