package com.photoizer.crm.auth.api;

import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "UserResponse", description = "Usuário do sistema (staff).")
public record UserResponse(
    @Schema(description = "Identificador do usuário")
    UUID id,
    @Schema(description = "E-mail do usuário", example = "fotografo@photoizer.com.br")
    String email,
    @Schema(description = "Nome do usuário", example = "João Fotógrafo")
    String nome,
    @Schema(description = "Papel do usuário (ADMIN, FOTOGRAFO, EDITOR, AGENDADOR)")
    Papel papel,
    @Schema(description = "Telefone de contato", nullable = true)
    String telefone,
    @Schema(description = "Indica se a conta está ativa")
    boolean ativo
) {
    public static UserResponse of(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getNome(), u.getPapel(), u.getTelefone(), u.isAtivo());
    }
}
