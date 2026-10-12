package com.photoizer.crm.auth.api;

import com.photoizer.crm.auth.model.Papel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Dados do usuário autenticado derivados do servidor (achado M4).
 * Não expõe tokens nem hash de senha.
 */
@Schema(name = "MeResponse", description = "Dados do usuário autenticado (fonte de verdade para o frontend).")
public record MeResponse(
    @Schema(description = "Nome do usuário", example = "Administrador")
    String nome,
    @Schema(description = "E-mail do usuário", example = "admin@photoizer.com.br")
    String email,
    @Schema(description = "Papel do usuário (ADMIN, FOTOGRAFO, EDITOR, AGENDADOR)")
    Papel papel,
    @Schema(description = "Identificador do usuário")
    UUID userId
) {}
