package com.photoizer.crm.cliente.api.dto;

import com.photoizer.crm.cliente.model.OrigemCliente;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de resposta para administração de clientes.
 * Inclui campos administrativos como data de cadastro e observações.
 */
@Schema(name = "ClienteAdminResponse", description = "Cliente (visão administrativa).")
public record ClienteAdminResponse(
    @Schema(description = "Identificador do cliente") UUID id,
    @Schema(description = "Nome completo", example = "Ana Souza") String nome,
    @Schema(description = "Telefone de contato", example = "(11) 99999-0000") String telefone,
    @Schema(description = "E-mail", example = "ana@example.com", nullable = true) String email,
    @Schema(description = "CPF", example = "123.456.789-00", nullable = true) String cpf,
    @Schema(description = "Cidade", example = "São Paulo", nullable = true) String cidade,
    @Schema(description = "UF", example = "SP", nullable = true) String estado,
    @Schema(description = "Origem do cliente", example = "INDICACAO") OrigemCliente origem,
    @Schema(description = "Observações internas", nullable = true) String observacoes,
    @Schema(description = "Data de cadastro") LocalDateTime dataCadastro,
    @Schema(description = "Preferências", nullable = true) String preferencias,
    @Schema(description = "Criação do registro") LocalDateTime createdAt,
    @Schema(description = "Última atualização do registro") LocalDateTime updatedAt
) {}
