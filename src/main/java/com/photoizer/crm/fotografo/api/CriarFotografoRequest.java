package com.photoizer.crm.fotografo.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarFotografoRequest(
    @NotBlank String nome,
    @NotBlank @Email String email,
    @Size(min = 6) String senha,
    String telefone
) {}