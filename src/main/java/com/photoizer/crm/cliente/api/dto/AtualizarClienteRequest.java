package com.photoizer.crm.cliente.api.dto;

import com.photoizer.crm.cliente.model.OrigemCliente;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO de requisição para atualização de cliente (admin).
 * Todos os campos obrigatórios para atualização completa.
 */
public record AtualizarClienteRequest(
    @NotBlank @Size(max = 255) String nome,
    @NotBlank @Size(max = 20) String telefone,
    @Email @Size(max = 255) String email,
    @Pattern(regexp = "\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}") @Size(max = 14) String cpf,
    @Size(max = 100) String cidade,
    @Size(max = 2) String estado,
    @NotNull OrigemCliente origem,
    String observacoes,
    String preferencias
) {}
