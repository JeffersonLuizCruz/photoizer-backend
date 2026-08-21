package com.photoizer.crm.cliente.api.dto;

import com.photoizer.crm.cliente.model.OrigemCliente;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de resposta para administração de clientes.
 * Inclui campos administrativos como data de cadastro e observações.
 */
public record ClienteAdminResponse(
    UUID id,
    String nome,
    String telefone,
    String email,
    String cpf,
    String cidade,
    String estado,
    OrigemCliente origem,
    String observacoes,
    LocalDateTime dataCadastro,
    String preferencias,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
