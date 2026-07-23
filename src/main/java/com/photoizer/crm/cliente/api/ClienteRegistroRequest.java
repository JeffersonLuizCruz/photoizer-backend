package com.photoizer.crm.cliente.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClienteRegistroRequest(
    @NotBlank String nome,
    @NotBlank @Email String email,
    @NotBlank String telefone,
    @NotBlank @Size(min = 6, max = 100) String senha,
    String preferencias
) {}
