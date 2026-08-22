package com.photoizer.crm.edicao.service;

import com.photoizer.crm.edicao.api.EdicaoMapper;
import com.photoizer.crm.edicao.api.EdicaoResponse;
import com.photoizer.crm.edicao.api.FotoEdicaoResponse;
import com.photoizer.crm.edicao.exception.EdicaoNaoEncontradaException;
import com.photoizer.crm.edicao.model.StatusEdicao;
import com.photoizer.crm.edicao.model.StatusFotoEdicao;
import com.photoizer.crm.edicao.repository.EdicaoRepository;
import com.photoizer.crm.edicao.repository.FotoEdicaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Facade Pattern — separa operações de leitura (read-only) de mutações.
 * Facilita cache, testes de integração e manutenção.
 * Padrão já adotado no módulo agenda (AgendamentoQueryService).
 */
@Service
@Transactional(readOnly = true)
public class EdicaoQueryService {

    private final EdicaoRepository edicaoRepository;
    private final FotoEdicaoRepository fotoEdicaoRepository;
    private final EdicaoMapper edicaoMapper;

    public EdicaoQueryService(EdicaoRepository edicaoRepository,
                              FotoEdicaoRepository fotoEdicaoRepository,
                              EdicaoMapper edicaoMapper) {
        this.edicaoRepository = edicaoRepository;
        this.fotoEdicaoRepository = fotoEdicaoRepository;
        this.edicaoMapper = edicaoMapper;
    }

    public EdicaoResponse obterStatus(UUID agendamentoId) {
        var edicao = edicaoRepository.findByAgendamentoId(agendamentoId)
            .orElse(null);
        if (edicao == null) {
            return null;
        }
        var counts = buscarCounts(edicao.getId());
        return edicaoMapper.toResponse(edicao, counts.raw(), counts.editadas());
    }

    public List<EdicaoResponse> listarTodos() {
        var edicoes = edicaoRepository.findAllByOrderByAuditInfoUpdatedAtDesc();
        return edicoes.stream()
            .map(e -> {
                var counts = buscarCounts(e.getId());
                return edicaoMapper.toResponse(e, counts.raw(), counts.editadas());
            })
            .toList();
    }

    public List<EdicaoResponse> listarPorStatus(StatusEdicao status) {
        var edicoes = edicaoRepository.findByStatusOrderByAuditInfoUpdatedAtDesc(status);
        return edicoes.stream()
            .map(e -> {
                var counts = buscarCounts(e.getId());
                return edicaoMapper.toResponse(e, counts.raw(), counts.editadas());
            })
            .toList();
    }

    public List<FotoEdicaoResponse> listarFotos(UUID agendamentoId) {
        var edicao = edicaoRepository.findByAgendamentoId(agendamentoId)
            .orElseThrow(() -> new EdicaoNaoEncontradaException("Processo de edição não encontrado para este ensaio"));
        return fotoEdicaoRepository.findByEdicaoIdOrderByOrdemAsc(edicao.getId()).stream()
            .map(edicaoMapper::toResponse)
            .toList();
    }

    public record Counts(int raw, int editadas) {}

    public Counts buscarCounts(UUID edicaoId) {
        var totalRaw = fotoEdicaoRepository.countByEdicaoIdAndStatus(edicaoId, StatusFotoEdicao.RAW);
        var totalEditadas = fotoEdicaoRepository.countByEdicaoIdAndStatus(edicaoId, StatusFotoEdicao.EDITADO);
        return new Counts(totalRaw, totalEditadas);
    }
}
