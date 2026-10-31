package com.photoizer.crm.ecommerce.event;

import java.util.UUID;

/** Lead criado no checkout público de pacotes. */
public record LeadPacoteCriadoEvent(
    UUID leadId,
    String pacoteNome,
    String clienteNome
) {}
