package com.photoizer.crm.auth.api;

import com.photoizer.crm.auth.model.Papel;

public record LoginResponse(
    String token,
    String nome,
    String email,
    Papel papel,
    String userId
) {}
