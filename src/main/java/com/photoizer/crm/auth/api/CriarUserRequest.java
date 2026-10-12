package com.photoizer.crm.auth.api;

import com.photoizer.crm.auth.model.Papel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(name = "CriarUserRequest", description = "Dados para criação de um usuário do sistema (staff).")
public record CriarUserRequest(
    @Schema(description = "E-mail único do usuário", example = "editor@photoizer.com.br")
    @NotBlank @Email String email,
    @Schema(description = "Senha inicial", example = "S3nh@Forte")
    @NotBlank String password,
    @Schema(description = "Nome completo", example = "Maria Editora")
    @NotBlank String nome,
    @Schema(description = "Papel do usuário", example = "EDITOR")
    @NotNull Papel papel,
    @Schema(description = "Telefone de contato", nullable = true)
    String telefone
) {}
