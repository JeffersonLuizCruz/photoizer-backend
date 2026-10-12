package com.photoizer.crm.cliente.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ClienteRegistroRequest", description = "Dados para autorregistro do cliente no e-commerce.")
public record ClienteRegistroRequest(
    @Schema(description = "Nome completo", example = "Ana Souza")
    @NotBlank String nome,
    @Schema(description = "E-mail (usado como login)", example = "ana@example.com")
    @NotBlank @Email String email,
    @Schema(description = "Telefone de contato", example = "(11) 99999-0000")
    @NotBlank String telefone,
    @Schema(description = "Senha (mínimo 6 caracteres)", example = "senha123")
    @NotBlank @Size(min = 6, max = 100) String senha,
    @Schema(description = "Preferências declaradas pelo cliente", nullable = true)
    String preferencias
) {}
