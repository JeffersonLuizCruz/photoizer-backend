package com.photoizer.crm.fotografo.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AtualizarFotografoRequest(
    @NotBlank String nome,
    @NotBlank @Email String email,
    String telefone
) {}