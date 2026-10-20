package com.photoizer.crm.shared.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Porta de leitura de bloqueios de agenda.
 *
 * Consumida pelo modulo agenda (DisponibilidadeService) e implementada pelo
 * modulo bloqueio, invertendo a dependencia e evitando acoplamento direto.
 */
public interface BloqueioAgendaPort {

    /**
     * Retorna bloqueios ativos que se sobrepoem a [inicio, fim).
     * Bloqueios globais sempre se aplicam; bloqueios de profissional somente
     * quando o profissionalId informado coincide. Quando profissionalId e nulo,
     * apenas bloqueios globais sao retornados.
     */
    List<BloqueioConflito> buscarBloqueios(LocalDateTime inicio, LocalDateTime fim, UUID profissionalId);

    record BloqueioConflito(
        UUID bloqueioId,
        String titulo,
        LocalDateTime inicio,
        LocalDateTime fim,
        UUID profissionalId
    ) {}
}
