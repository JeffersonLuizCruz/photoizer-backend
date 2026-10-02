package com.photoizer.crm.auth.api;

import com.photoizer.crm.auth.model.Papel;
import java.util.UUID;

/**
 * Dados do usuário autenticado derivados do servidor (achado M4).
 * Não expõe tokens nem hash de senha.
 */
public record MeResponse(
    String nome,
    String email,
    Papel papel,
    UUID userId
) {}
