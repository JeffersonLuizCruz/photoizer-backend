package com.photoizer.crm.auth.adapter;

import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.shared.port.UsuarioStaffPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Implementacao da porta UsuarioStaffPort para consulta de usuarios staff
 * sem acoplar o modulo consumidor ao modulo auth.
 */
@Component
public class UsuarioStaffPortAdapter implements UsuarioStaffPort {

    private static final List<Papel> PAPEIS_STAFF = List.of(
        Papel.ADMIN,
        Papel.EDITOR,
        Papel.AGENDADOR
    );

    private static final List<Papel> PAPEIS_APROVADORES_COMPRAS = List.of(
        Papel.ADMIN,
        Papel.FOTOGRAFO
    );

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

    @Override
    public List<UUID> listarStaffAtivos() {
        return userRepository.findByPapelInAndAtivoTrue(PAPEIS_STAFF)
            .stream()
            .map(User::getId)
            .toList();
    }

    @Override
    public List<UUID> listarAprovadoresCompras() {
        return userRepository.findByPapelInAndAtivoTrue(PAPEIS_APROVADORES_COMPRAS)
            .stream()
            .map(User::getId)
            .toList();
    }
}
