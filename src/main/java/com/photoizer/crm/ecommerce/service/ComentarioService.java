package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.api.ComentarioRequest;
import com.photoizer.crm.ecommerce.api.ComentarioResponse;
import com.photoizer.crm.ecommerce.api.ComentariosPorFotoResponse;
import com.photoizer.crm.ecommerce.api.EcommerceMapper;
import com.photoizer.crm.ecommerce.exception.FotoIndisponivelException;
import com.photoizer.crm.ecommerce.model.FotoComentario;
import com.photoizer.crm.ecommerce.model.OrigemComentario;
import com.photoizer.crm.ecommerce.repository.FotoComentarioRepository;
import com.photoizer.crm.foto.api.FotoEnsaioResponse;
import com.photoizer.crm.foto.model.StatusFoto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Comentários por foto: clientes comentam via token da galeria e o fotógrafo/
 * admin visualiza e responde no painel administrativo.
 *
 * MODULITH: Usa GaleriaQueryService para queries read-only em FotoEnsaio.
 * O módulo ecommerce NÃO deve escrever diretamente em FotoEnsaio.
 */
@Service
public class ComentarioService {

    private final GaleriaQueryService galeriaQueryService;
    private final FotoComentarioRepository comentarioRepository;
    private final EcommerceMapper ecommerceMapper;

    public ComentarioService(GaleriaQueryService galeriaQueryService,
                             FotoComentarioRepository comentarioRepository,
                             EcommerceMapper ecommerceMapper) {
        this.galeriaQueryService = galeriaQueryService;
        this.comentarioRepository = comentarioRepository;
        this.ecommerceMapper = ecommerceMapper;
    }

    private void validarFotoPertencenteAoAgendamento(UUID agendamentoId, UUID fotoId) {
        var foto = galeriaQueryService.buscarFotoPorId(fotoId);
        if (!foto.getAgendamentoId().equals(agendamentoId)) {
            throw new FotoIndisponivelException("A foto não pertence a esta galeria");
        }
    }

    @Transactional
    public ComentarioResponse comentarCliente(UUID token, UUID fotoId, ComentarioRequest request) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        var foto = galeriaQueryService.buscarFotoPorId(fotoId);
        if (!foto.getAgendamentoId().equals(agendamento.getId())) {
            throw new FotoIndisponivelException("A foto não pertence a esta galeria");
        }
        if (!foto.isVisivel() || (foto.getStatus() != StatusFoto.PUBLICADA && foto.getStatus() != StatusFoto.PAGA)) {
            throw new FotoIndisponivelException("Esta foto não está disponível para comentários");
        }
        var comentario = FotoComentario.builder()
            .fotoId(foto.getId())
            .agendamentoId(agendamento.getId())
            .autorNome(normalizarNome(request.autorNome()))
            .mensagem(request.mensagem().trim())
            .origem(OrigemComentario.CLIENTE)
            .lida(false)
            .build();
        return ecommerceMapper.toComentarioResponse(comentarioRepository.save(comentario));
    }

    @Transactional(readOnly = true)
    public List<ComentarioResponse> listarCliente(UUID token, UUID fotoId) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        validarFotoPertencenteAoAgendamento(agendamento.getId(), fotoId);
        return comentarioRepository.findByFotoIdOrderByAuditInfoCreatedAtAsc(fotoId).stream()
            .map(ecommerceMapper::toComentarioResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ComentariosPorFotoResponse> listarAdmin(UUID agendamentoId) {
        var comentarios = comentarioRepository.findByAgendamentoIdOrderByAuditInfoCreatedAtAsc(agendamentoId);

        var porFoto = new LinkedHashMap<UUID, List<FotoComentario>>();
        for (var comentario : comentarios) {
            porFoto.computeIfAbsent(comentario.getFotoId(), k -> new ArrayList<>()).add(comentario);
        }

        return galeriaQueryService.listarFotosPorAgendamento(agendamentoId).stream()
            .map(foto -> new ComentariosPorFotoResponse(
                FotoEnsaioResponse.of(foto),
                porFoto.getOrDefault(foto.getId(), List.of()).stream()
                    .map(ecommerceMapper::toComentarioResponse)
                    .toList(),
                porFoto.getOrDefault(foto.getId(), List.of()).stream()
                    .filter(c -> c.getOrigem() == OrigemComentario.CLIENTE && !c.isLida())
                    .count()
            ))
            .toList();
    }

    @Transactional
    public ComentarioResponse responderStaff(UUID agendamentoId, UUID fotoId, ComentarioRequest request) {
        validarFotoPertencenteAoAgendamento(agendamentoId, fotoId);
        var comentario = FotoComentario.builder()
            .fotoId(fotoId)
            .agendamentoId(agendamentoId)
            .autorNome(normalizarNome(request.autorNome()))
            .mensagem(request.mensagem().trim())
            .origem(OrigemComentario.STAFF)
            .lida(true)
            .build();
        var salvo = comentarioRepository.save(comentario);
        marcarLidos(agendamentoId, fotoId);
        return ecommerceMapper.toComentarioResponse(salvo);
    }

    @Transactional
    public void marcarLidos(UUID agendamentoId, UUID fotoId) {
        var comentarios = comentarioRepository.findByFotoIdOrderByAuditInfoCreatedAtAsc(fotoId).stream()
            .filter(c -> c.getAgendamentoId().equals(agendamentoId))
            .filter(c -> c.getOrigem() == OrigemComentario.CLIENTE && !c.isLida())
            .toList();
        if (comentarios.isEmpty()) return;
        comentarios.forEach(c -> c.setLida(true));
        comentarioRepository.saveAll(comentarios);
    }

    private String normalizarNome(String nome) {
        if (nome == null) return null;
        var limpo = nome.trim();
        return limpo.isBlank() ? null : limpo;
    }
}
