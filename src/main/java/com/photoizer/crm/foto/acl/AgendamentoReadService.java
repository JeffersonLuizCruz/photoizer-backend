package com.photoizer.crm.foto.acl;

import java.util.UUID;

/**
 * PORTA: Anti-Corruption Layer — abstrai leitura do módulo agenda.
 * Motivo: o módulo foto não deve depender diretamente de AgendamentoRepository
 * ou StatusAgendamento. Esta interface define o contrato mínimo necessário
 * para validar se um agendamento permite upload de fotos.
 */
public interface AgendamentoReadService {
    boolean isStatusPermitidoParaUpload(UUID agendamentoId);
}
