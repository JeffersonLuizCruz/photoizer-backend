package com.photoizer.crm.cliente.api;

import java.util.UUID;

public record ClienteAuthResponse(
    String token,
    UUID id,
    String nome,
    String email,
    String telefone
) {}
