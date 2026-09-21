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
        return pacoteMapper.toResponse(pacoteRepository.save(pacote));
    }

    public PacoteResponse atualizar(UUID id, PacoteRequest request) {
        var pacote = pacoteRepository.findById(id)
            .orElseThrow(() -> new PacoteNaoEncontradoException(id));
        pacoteMapper.updateEntity(request, pacote);
        return pacoteMapper.toResponse(pacoteRepository.save(pacote));
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
    }
}
