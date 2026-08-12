package com.photoizer.crm.contrato.api;

import java.util.UUID;

public record PublicarContratoResponse(
    UUID contratoId,
    String url
) {
}