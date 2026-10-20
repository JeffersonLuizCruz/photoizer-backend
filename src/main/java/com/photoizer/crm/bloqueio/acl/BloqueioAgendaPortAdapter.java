package com.photoizer.crm.bloqueio.acl;

import com.photoizer.crm.bloqueio.repository.BloqueioAgendaRepository;
import com.photoizer.crm.shared.port.BloqueioAgendaPort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Implementacao da porta BloqueioAgendaPort para o modulo agenda.
 */
@Component
public class BloqueioAgendaPortAdapter implements BloqueioAgendaPort {

    private final BloqueioAgendaRepository bloqueioRepository;

    public BloqueioAgendaPortAdapter(BloqueioAgendaRepository bloqueioRepository) {
        this.bloqueioRepository = bloqueioRepository;
    }

    @Override
    public List<BloqueioConflito> buscarBloqueios(LocalDateTime inicio, LocalDateTime fim, UUID profissionalId) {
        return bloqueioRepository.findAtivosAplicaveisNoPeriodo(inicio, fim, profissionalId)
            .stream()
            .map(b -> new BloqueioConflito(
                b.getId(), b.getTitulo(), b.getInicio(), b.getFim(), b.getProfissionalId()))
            .toList();
    }
}
