package com.photoizer.crm.auth.api;

import com.photoizer.crm.auth.model.Papel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "LoginResponse", description = "Resultado do login. Os tokens também são gravados em cookies HttpOnly.")
public record LoginResponse(
    @Schema(description = "Access token (JWT) para uso via header Authorization: Bearer")
    String token,
    @Schema(description = "Refresh token (rotacionado a cada renovação)")
    String refreshToken,
    @Schema(description = "Nome do usuário", example = "Administrador")
    String nome,
    @Schema(description = "E-mail do usuário", example = "admin@photoizer.com.br")
    String email,
    @Schema(description = "Papel do usuário (ADMIN, FOTOGRAFO, EDITOR, AGENDADOR)")
    Papel papel,
    @Schema(description = "Identificador do usuário")
    UUID userId
) {}
