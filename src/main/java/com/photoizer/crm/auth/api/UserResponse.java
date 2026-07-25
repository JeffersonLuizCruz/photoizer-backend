package com.photoizer.crm.auth.api;

import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String nome,
    Papel papel,
    String telefone,
    boolean ativo
) {
    public static UserResponse of(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getNome(), u.getPapel(), u.getTelefone(), u.isAtivo());
    }
}
