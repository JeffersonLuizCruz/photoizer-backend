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

    /**
     * Status em que o ensaio já teve o pagamento final registrado e, portanto,
     * permite upload e publicação de fotos no ecommerce.
     */
    private static final List<StatusAgendamento> STATUS_PERMITIDOS_ECOMMERCE = List.of(
        StatusAgendamento.EM_EDICAO,
        StatusAgendamento.FINALIZADO
    );

    private final AgendamentoRepository agendamentoRepository;

    public AgendamentoReadServiceAdapter(AgendamentoRepository agendamentoRepository) {
        this.agendamentoRepository = agendamentoRepository;
    }

    @Override
    public boolean isStatusPermitidoParaUpload(UUID agendamentoId) {
        return agendamentoRepository.findById(agendamentoId)
            .map(a -> STATUS_PERMITIDOS_ECOMMERCE.contains(a.getStatus()))
            .orElse(false);
    }

    @Override
    public boolean isStatusPermitidoParaPublicacao(UUID agendamentoId) {
        return agendamentoRepository.findById(agendamentoId)
            .map(a -> STATUS_PERMITIDOS_ECOMMERCE.contains(a.getStatus()))
            .orElse(false);
    }
}
