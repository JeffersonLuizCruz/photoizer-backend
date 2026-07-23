package com.photoizer.crm.cliente.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ClienteLoginRequest(
    @NotBlank @Email String email,
    @NotBlank String senha
) {}
