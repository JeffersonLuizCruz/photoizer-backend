package com.photoizer.crm.agenda.acl;

import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.foto.acl.AgendamentoReadService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Adapter do módulo agenda para a porta AgendamentoReadService.
 * Implementa a lógica de verificação de status permitido para upload,
 * mantendo a regra de negócio no módulo dono (agenda).
 */
@Service
public class AgendamentoReadServiceAdapter implements AgendamentoReadService {

    private static final List<StatusAgendamento> STATUS_ALLOW_UPLOAD = List.of(
        StatusAgendamento.EM_EDICAO,
        StatusAgendamento.SELECAO_DAS_FOTOS,
        StatusAgendamento.FOTOS_ENVIADAS_PARA_SELECAO,
        StatusAgendamento.FOTOS_ENTREGUES
    );

    private final AgendamentoRepository agendamentoRepository;

    public AgendamentoReadServiceAdapter(AgendamentoRepository agendamentoRepository) {
        this.agendamentoRepository = agendamentoRepository;
    }

    @Override
    public boolean isStatusPermitidoParaUpload(UUID agendamentoId) {
        return agendamentoRepository.findById(agendamentoId)
            .map(a -> STATUS_ALLOW_UPLOAD.contains(a.getStatus()))
            .orElse(false);
    }
}
