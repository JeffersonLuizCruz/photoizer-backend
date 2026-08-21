package com.photoizer.crm.cliente.api.dto;

import com.photoizer.crm.cliente.model.OrigemCliente;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de resposta para o cliente.
 * Exclui dados sensíveis como senhaHash.
 */
public record ClienteResponse(
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
    String preferencias
) {}
