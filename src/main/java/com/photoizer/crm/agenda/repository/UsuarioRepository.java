package com.photoizer.crm.agenda.repository;

import com.photoizer.crm.agenda.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
}
