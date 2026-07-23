package com.photoizer.crm.auth.api;

public record LoginResponse(
    String token,
    String nome,
    String email
) {}
