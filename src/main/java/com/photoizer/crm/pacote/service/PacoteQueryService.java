package com.photoizer.crm.pacote.service;

import com.photoizer.crm.pacote.api.PacoteMapper;
import com.photoizer.crm.pacote.api.PacoteResponse;
import com.photoizer.crm.pacote.exception.PacoteInativoException;
import com.photoizer.crm.pacote.exception.PacoteNaoEncontradoException;
import com.photoizer.crm.pacote.model.Pacote;
import com.photoizer.crm.pacote.repository.PacoteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Design Pattern: Facade / Query Service
 *
 * Motivo: Separar operações de leitura de escrita (CQRS leve) e expor
 * uma API pública controlada para que outros módulos (agenda, contrato,
 * financeiro) busquem dados do pacote sem depender do PacoteRepository.
 *
 * Antes: 3 módulos injetavam PacoteRepository diretamente, violando
 * o princípio de que módulos não devem acessar repositórios de outros
 * módulos no Spring Modulith.
 *
 * Agora: módulos consumidores injetam PacoteQueryService (interface
 * pública do módulo pacote para leituras cross-module).
 */
@Service
@Transactional(readOnly = true)
public class PacoteQueryService {

    private final PacoteRepository pacoteRepository;
    private final PacoteMapper pacoteMapper;

    public PacoteQueryService(PacoteRepository pacoteRepository, PacoteMapper pacoteMapper) {
        this.pacoteRepository = pacoteRepository;
        this.pacoteMapper = pacoteMapper;
    }

    public PacoteResponse buscarPorId(UUID id) {
        return pacoteRepository.findById(id)
            .map(pacoteMapper::toResponse)
            .orElseThrow(() -> new PacoteNaoEncontradoException(id));
    }

    public Pacote buscarEntityPorId(UUID id) {
        return pacoteRepository.findById(id)
            .orElseThrow(() -> new PacoteNaoEncontradoException(id));
    }

    public List<PacoteResponse> listarTodos() {
        return pacoteRepository.findAll().stream()
            .map(pacoteMapper::toResponse)
            .toList();
    }

    public Page<PacoteResponse> listarPaginado(String search, Pageable pageable) {
        Page<Pacote> page;
        if (search != null && !search.isBlank()) {
            page = pacoteRepository.findByNomeContainingIgnoreCase(search, pageable);
        } else {
            page = pacoteRepository.findAll(pageable);
        }
        return page.map(pacoteMapper::toResponse);
    }

    public void validarAtivo(UUID id) {
        var pacote = pacoteRepository.findById(id)
            .orElseThrow(() -> new PacoteNaoEncontradoException(id));
        if (!pacote.getAtivo()) {
            throw new PacoteInativoException(id);
        }
    }
}
