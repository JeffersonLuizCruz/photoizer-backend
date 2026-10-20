package com.photoizer.crm.shared.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Porta de consulta de agendamentos que ocupam a agenda.
 *
 * Consumida pelo modulo bloqueio ao criar/editar um compromisso, garantindo
 * que um bloqueio nao seja sobreposto a um ensaio ativo. Implementada pelo
 * modulo agenda.
 */
public interface AgendamentoOcupacaoPort {

    /**
     * Retorna ensaios ativos que se sobrepoem a [inicio, fim).
     * Quando fotografoId e nulo, considera qualquer profissional.
     */
    List<ConflitoAgendamento> buscarConflitos(LocalDateTime inicio, LocalDateTime fim, UUID fotografoId);

    record ConflitoAgendamento(
        UUID agendamentoId,
        LocalDateTime dataHoraEnsaio,
        int duracaoMinutos,
        String localEnsaio
    ) {}
}
