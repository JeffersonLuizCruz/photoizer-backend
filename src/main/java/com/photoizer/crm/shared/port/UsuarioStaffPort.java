package com.photoizer.crm.shared.port;

import java.util.UUID;

/**
 * Porta minima de consulta de usuarios staff, usada pelo modulo bloqueio
 * para validar o profissional informado sem acoplar ao modulo auth.
 */
public interface UsuarioStaffPort {

    boolean existeStaffAtivo(UUID usuarioId);
}
