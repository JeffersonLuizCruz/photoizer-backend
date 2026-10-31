package com.photoizer.crm.ecommerce.api;

import java.util.UUID;

/** Resposta pública minimalista: não devolve os dados pessoais do lead. */
public record LeadPacotePublicoResponse(
    UUID id,
    String status
) {}
