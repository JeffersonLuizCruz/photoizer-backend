package com.photoizer.crm.auth.adapter;

import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.shared.port.UsuarioStaffPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Implementacao da porta UsuarioStaffPort para consulta de usuarios staff
 * sem acoplar o modulo consumidor ao modulo auth.
 */
@Component
public class UsuarioStaffPortAdapter implements UsuarioStaffPort {

    private final UserRepository userRepository;

    public UsuarioStaffPortAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean existeStaffAtivo(UUID usuarioId) {
        return userRepository.findById(usuarioId)
            .map(user -> user.isAtivo() && user.getPapel() != null)
            .orElse(false);
    }
}
