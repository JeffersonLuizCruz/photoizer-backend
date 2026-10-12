package com.photoizer.crm.cliente.api.dto;

import com.photoizer.crm.cliente.model.OrigemCliente;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO de requisição para criação de cliente (admin).
 * Validação dos dados obrigatórios para criação.
 */
@Schema(name = "CriarClienteRequest", description = "Dados para criação de um cliente (admin).")
public record CriarClienteRequest(
    @Schema(description = "Nome completo", example = "Ana Souza")
    @NotBlank @Size(max = 255) String nome,
    @Schema(description = "Telefone de contato", example = "(11) 99999-0000")
    @NotBlank @Size(max = 20) String telefone,
    @Schema(description = "E-mail", example = "ana@example.com", nullable = true)
    @Email @Size(max = 255) String email,
    @Schema(description = "CPF no formato 000.000.000-00", example = "123.456.789-00", nullable = true)
    @Pattern(regexp = "\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}") @Size(max = 14) String cpf,
    @Schema(description = "Cidade", example = "São Paulo", nullable = true)
    @Size(max = 100) String cidade,
    @Schema(description = "UF", example = "SP", nullable = true)
    @Size(max = 2) String estado,
    @Schema(description = "Origem do cliente", example = "INDICACAO")
    @NotNull OrigemCliente origem,
    @Schema(description = "Observações internas", nullable = true) String observacoes,
    @Schema(description = "Preferências", nullable = true) String preferencias
) {}
