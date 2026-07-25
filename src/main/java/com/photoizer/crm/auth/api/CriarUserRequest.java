package com.photoizer.crm.auth.api;

import com.photoizer.crm.auth.model.Papel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CriarUserRequest(
    @NotBlank @Email String email,
    @NotBlank String password,
    @NotBlank String nome,
    @NotNull Papel papel,
    String telefone
) {}
