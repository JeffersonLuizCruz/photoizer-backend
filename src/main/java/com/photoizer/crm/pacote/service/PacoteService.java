package com.photoizer.crm.pacote.service;

import com.photoizer.crm.agenda.model.Usuario;
import com.photoizer.crm.agenda.repository.UsuarioRepository;
import com.photoizer.crm.pacote.api.PacoteRequest;
import com.photoizer.crm.pacote.api.PacoteResponse;
import com.photoizer.crm.pacote.exception.PacoteInativoException;
import com.photoizer.crm.pacote.exception.PacoteNaoEncontradoException;
import com.photoizer.crm.pacote.model.Pacote;
import com.photoizer.crm.pacote.repository.PacoteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PacoteService {

    private final PacoteRepository pacoteRepository;
    private final UsuarioRepository usuarioRepository;

    public PacoteService(PacoteRepository pacoteRepository, UsuarioRepository usuarioRepository) {
        this.pacoteRepository = pacoteRepository;
        this.usuarioRepository = usuarioRepository;
    }

    private Usuario buscarUsuario(UUID id) {
        return id != null ? usuarioRepository.findById(id).orElse(null) : null;
    }

    @Transactional(readOnly = true)
    public List<PacoteResponse> listarTodos() {
        return pacoteRepository.findAll().stream()
            .map(PacoteResponse::of)
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<PacoteResponse> listarPaginado(String search, Pageable pageable) {
        Page<Pacote> page;
        if (search != null && !search.isBlank()) {
            page = pacoteRepository.findByNomeContainingIgnoreCase(search, pageable);
        } else {
            page = pacoteRepository.findAll(pageable);
        }
        return page.map(PacoteResponse::of);
    }

    @Transactional(readOnly = true)
    public PacoteResponse buscarPorId(UUID id) {
        return pacoteRepository.findById(id)
            .map(PacoteResponse::of)
            .orElseThrow(() -> new PacoteNaoEncontradoException(id));
    }

    @Transactional(readOnly = true)
    public Pacote buscarEntityPorId(UUID id) {
        return pacoteRepository.findById(id)
            .orElseThrow(() -> new PacoteNaoEncontradoException(id));
    }

    public PacoteResponse criar(PacoteRequest request) {
        var pacote = Pacote.builder()
            .nome(request.nome())
            .descricao(request.descricao())
            .quantidadeFotos(request.quantidadeFotos())
            .quantidadeVideos(request.quantidadeVideos())
            .valorBase(request.valorBase())
            .precoFotoExtra(request.precoFotoExtra() != null ? request.precoFotoExtra() : BigDecimal.valueOf(15))
            .imagemCapa(request.imagemCapa())
            .beneficios(request.beneficios())
            .duracaoEstimada(request.duracaoEstimada())
            .bloqueiaDiaInteiro(request.bloqueiaDiaInteiro())
            .ativo(request.ativo())
            .fotografo(buscarUsuario(request.fotografoId()))
            .editorResponsavel(buscarUsuario(request.editorResponsavelId()))
            .diasParaEntrega(request.diasParaEntrega())
            .build();
        return PacoteResponse.of(pacoteRepository.save(pacote));
    }

    public PacoteResponse atualizar(UUID id, PacoteRequest request) {
        var pacote = pacoteRepository.findById(id)
            .orElseThrow(() -> new PacoteNaoEncontradoException(id));
        pacote.setNome(request.nome());
        pacote.setDescricao(request.descricao());
        pacote.setQuantidadeFotos(request.quantidadeFotos());
        pacote.setQuantidadeVideos(request.quantidadeVideos());
        pacote.setValorBase(request.valorBase());
        pacote.setPrecoFotoExtra(request.precoFotoExtra() != null ? request.precoFotoExtra() : pacote.getPrecoFotoExtra());
        pacote.setImagemCapa(request.imagemCapa());
        pacote.setBeneficios(request.beneficios());
        pacote.setDuracaoEstimada(request.duracaoEstimada());
        pacote.setBloqueiaDiaInteiro(request.bloqueiaDiaInteiro());
        pacote.setAtivo(request.ativo());
        pacote.setFotografo(buscarUsuario(request.fotografoId()));
        pacote.setEditorResponsavel(buscarUsuario(request.editorResponsavelId()));
        pacote.setDiasParaEntrega(request.diasParaEntrega());
        return PacoteResponse.of(pacoteRepository.save(pacote));
    }

    public void deletar(UUID id) {
        if (!pacoteRepository.existsById(id)) {
            throw new PacoteNaoEncontradoException(id);
        }
        pacoteRepository.deleteById(id);
    }

    public void validarAtivo(UUID id) {
        var pacote = pacoteRepository.findById(id)
            .orElseThrow(() -> new PacoteNaoEncontradoException(id));
        if (!pacote.getAtivo()) {
            throw new PacoteInativoException(id);
        }
    }
}
