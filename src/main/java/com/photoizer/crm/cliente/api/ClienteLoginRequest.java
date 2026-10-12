package com.photoizer.crm.cliente.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "ClienteLoginRequest", description = "Credenciais de login do cliente.")
public record ClienteLoginRequest(
    @Schema(description = "E-mail do cliente", example = "ana@example.com")
    @NotBlank @Email String email,
    @Schema(description = "Senha do cliente", example = "senha123")
    @NotBlank String senha
) {}
