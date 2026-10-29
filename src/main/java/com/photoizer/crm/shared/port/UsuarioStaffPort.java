package com.photoizer.crm.shared.port;

import java.util.List;
import java.util.UUID;

/**
 * Porta minima de consulta de usuarios staff, usada pelos modulos bloqueio
 * (validacao de profissional) e notificacao (destinatarios de lembretes)
 * sem acoplar esses modulos ao modulo auth.
 */
public interface UsuarioStaffPort {

    boolean existeStaffAtivo(UUID usuarioId);

    /**
     * Identificadores dos usuarios staff ativos que devem receber avisos
     * operacionais (ADMIN, EDITOR e AGENDADOR).
     */
    List<UUID> listarStaffAtivos();

    /**
     * Identificadores dos usuarios ativos que podem aprovar compras de extras
     * (ADMIN e FOTOGRAFO). Usado para direcionar notificacoes que contem valor
     * financeiro sem expor o dado a papeis sem essa permissao.
     */
    List<UUID> listarAprovadoresCompras();
}
