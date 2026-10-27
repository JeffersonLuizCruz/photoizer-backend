package com.photoizer.crm.foto.acl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PORTA: Anti-Corruption Layer — leitura do módulo agenda para o painel de
 * armazenamento e para a regra de expurgo.
 *
 * <p>Separada de {@link AgendamentoReadService} (que trata de upload e
 * publicação) para não misturar responsabilidades. O módulo {@code foto}
 * continua sem depender de repositórios ou enums do {@code agenda}.
 */
public interface AgendamentoArmazenamentoReadService {

    List<AgendamentoResumo> listar();

    Optional<AgendamentoResumo> buscar(UUID agendamentoId);

    /** O expurgo só é permitido em ensaios finalizados. */
    boolean isFinalizado(UUID agendamentoId);
}
