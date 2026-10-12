package com.photoizer.crm.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "Credenciais de autenticação do staff.")
public record LoginRequest(
    @Schema(description = "E-mail do usuário", example = "admin@photoizer.com.br")
    @NotBlank String email,
    @Schema(description = "Senha em texto puro (transmitida apenas via HTTPS)", example = "dev123")
    @NotBlank String password
) {}
