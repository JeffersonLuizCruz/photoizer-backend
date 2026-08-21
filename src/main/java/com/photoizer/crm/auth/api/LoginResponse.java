package com.photoizer.crm.auth.api;

import com.photoizer.crm.auth.model.Papel;
import java.util.UUID;

public record LoginResponse(
    String token,
    String refreshToken,
    String nome,
    String email,
    Papel papel,
    UUID userId
) {}
