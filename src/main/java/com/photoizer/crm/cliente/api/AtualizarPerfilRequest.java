package com.photoizer.crm.cliente.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AtualizarPerfilRequest(
    @NotBlank String nome,
    @NotBlank String telefone,
    @Email String email,
    String cpf,
    String cidade,
    String estado
) {}
