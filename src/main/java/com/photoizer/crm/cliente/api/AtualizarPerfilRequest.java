package com.photoizer.crm.cliente.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "AtualizarPerfilRequest", description = "Dados de atualização do perfil do cliente autenticado.")
public record AtualizarPerfilRequest(
    @Schema(description = "Nome completo", example = "Ana Souza") @NotBlank String nome,
    @Schema(description = "Telefone de contato", example = "(11) 99999-0000") @NotBlank String telefone,
    @Schema(description = "E-mail", example = "ana@example.com", nullable = true) @Email String email,
    @Schema(description = "CPF no formato 000.000.000-00", example = "123.456.789-00", nullable = true) String cpf,
    @Schema(description = "Cidade", example = "São Paulo", nullable = true) String cidade,
    @Schema(description = "UF", example = "SP", nullable = true) String estado
) {}
