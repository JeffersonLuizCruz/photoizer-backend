package com.photoizer.crm.cliente.api.dto;

import com.photoizer.crm.cliente.model.OrigemCliente;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de resposta para o cliente.
 * Exclui dados sensíveis como senhaHash.
 */
@Schema(name = "ClienteResponse", description = "Dados do cliente (visão do próprio cliente).")
public record ClienteResponse(
    @Schema(description = "Identificador do cliente") UUID id,
    @Schema(description = "Nome completo", example = "Ana Souza") String nome,
    @Schema(description = "Telefone de contato", example = "(11) 99999-0000") String telefone,
    @Schema(description = "E-mail", nullable = true) String email,
    @Schema(description = "CPF", nullable = true) String cpf,
    @Schema(description = "Cidade", nullable = true) String cidade,
    @Schema(description = "UF", nullable = true) String estado,
    @Schema(description = "Origem do cliente", example = "INDICACAO") OrigemCliente origem,
    @Schema(description = "Observações", nullable = true) String observacoes,
    @Schema(description = "Data de cadastro") LocalDateTime dataCadastro,
    @Schema(description = "Preferências", nullable = true) String preferencias
) {}
