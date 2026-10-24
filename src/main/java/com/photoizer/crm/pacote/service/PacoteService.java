package com.photoizer.crm.pacote.service;

import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.pacote.api.PacoteMapper;
import com.photoizer.crm.pacote.api.PacoteRequest;
import com.photoizer.crm.pacote.api.PacoteResponse;
import com.photoizer.crm.pacote.exception.PacoteInativoException;
import com.photoizer.crm.pacote.exception.PacoteNaoEncontradoException;
import com.photoizer.crm.pacote.exception.PacoteVinculadoAgendamentoException;
import com.photoizer.crm.pacote.repository.PacoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service de escrita (command) do módulo pacote.
 * Operações de leitura estão em {@link PacoteQueryService}.
 */
@Service
@Transactional
public class PacoteService {

    private static final Logger log = LoggerFactory.getLogger(PacoteService.class);

    private final PacoteRepository pacoteRepository;
    private final PacoteMapper pacoteMapper;
    private final AgendamentoRepository agendamentoRepository;

    public PacoteService(PacoteRepository pacoteRepository, PacoteMapper pacoteMapper,
                         AgendamentoRepository agendamentoRepository) {
        this.pacoteRepository = pacoteRepository;
        this.pacoteMapper = pacoteMapper;
        this.agendamentoRepository = agendamentoRepository;
    }

    public PacoteResponse criar(PacoteRequest request) {
        var pacote = pacoteMapper.toEntity(request);
        pacote = pacoteRepository.save(pacote);
        log.atInfo()
            .addKeyValue("event_action", "pacote.criado")
            .addKeyValue("pacote_id", pacote.getId())
            .log("Pacote criado");
        return pacoteMapper.toResponse(pacote);
    }

    public PacoteResponse atualizar(UUID id, PacoteRequest request) {
        var pacote = pacoteRepository.findById(id)
            .orElseThrow(() -> new PacoteNaoEncontradoException(id));
        pacoteMapper.updateEntity(request, pacote);
        pacote = pacoteRepository.save(pacote);
        log.atInfo()
            .addKeyValue("event_action", "pacote.atualizado")
            .addKeyValue("pacote_id", pacote.getId())
            .log("Pacote atualizado");
        return pacoteMapper.toResponse(pacote);
    }

    public void deletar(UUID id) {
        var pacote = pacoteRepository.findById(id)
            .orElseThrow(() -> new PacoteNaoEncontradoException(id));

        if (!pacote.getAtivo()) {
            throw new PacoteInativoException(id);
        }

        boolean possuiAgendamentosAtivos = agendamentoRepository
            .existsByPacoteIdAndStatusNot(id, StatusAgendamento.CANCELADO);

        if (possuiAgendamentosAtivos) {
            long total = agendamentoRepository.countByPacoteIdAndStatusNot(id, StatusAgendamento.CANCELADO);
            throw new PacoteVinculadoAgendamentoException(id, total);
        }

        pacote.setAtivo(false);
        pacoteRepository.save(pacote);
        log.atInfo()
            .addKeyValue("event_action", "pacote.desativado")
            .addKeyValue("pacote_id", id)
            .log("Pacote desativado");
    }
}
