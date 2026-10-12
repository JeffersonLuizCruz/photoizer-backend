package com.photoizer.crm.cliente.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "ClienteAuthResponse", description = "Resultado do registro/login do cliente.")
public record ClienteAuthResponse(
    @Schema(description = "Access token (JWT) do cliente") String token,
    @Schema(description = "Identificador do cliente") UUID id,
    @Schema(description = "Nome do cliente", example = "Ana Souza") String nome,
    @Schema(description = "E-mail do cliente", example = "ana@example.com") String email,
    @Schema(description = "Telefone do cliente", example = "(11) 99999-0000") String telefone
) {}
