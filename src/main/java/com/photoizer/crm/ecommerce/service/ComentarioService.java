package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.ecommerce.api.ComentarioRequest;
import com.photoizer.crm.ecommerce.api.ComentarioResponse;
import com.photoizer.crm.ecommerce.api.ComentariosPorFotoResponse;
import com.photoizer.crm.ecommerce.exception.TokenExpiradoException;
import com.photoizer.crm.ecommerce.model.FotoComentario;
import com.photoizer.crm.ecommerce.model.OrigemComentario;
import com.photoizer.crm.ecommerce.repository.FotoComentarioRepository;
import com.photoizer.crm.foto.api.FotoEnsaioResponse;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Comentários por foto: clientes comentam via token da galeria e o fotógrafo/
 * admin visualiza e responde no painel administrativo.
 */
@Service
public class ComentarioService {

    private final AgendamentoRepository agendamentoRepository;
    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final FotoComentarioRepository comentarioRepository;

    public ComentarioService(AgendamentoRepository agendamentoRepository,
                             FotoEnsaioRepository fotoEnsaioRepository,
                             FotoComentarioRepository comentarioRepository) {
        this.agendamentoRepository = agendamentoRepository;
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.comentarioRepository = comentarioRepository;
    }

    @Transactional(readOnly = true)
    public Agendamento buscarAgendamentoPorToken(UUID token) {
        var agendamento = agendamentoRepository.findByTokenGaleria(token)
            .orElseThrow(() -> new RuntimeException("Galeria não encontrada"));
        if (agendamento.getTokenExpiracao() != null && agendamento.getTokenExpiracao().isBefore(LocalDateTime.now())) {
            throw new TokenExpiradoException("O link da galeria expirou. Solicite um novo link ao fotógrafo.");
        }
        return agendamento;
    }

    private FotoEnsaio validarFotoPertencenteAoAgendamento(UUID agendamentoId, UUID fotoId) {
        var foto = fotoEnsaioRepository.findById(fotoId)
            .orElseThrow(() -> new RuntimeException("Foto não encontrada"));
        if (!foto.getAgendamentoId().equals(agendamentoId)) {
            throw new IllegalArgumentException("A foto não pertence a esta galeria");
        }
        return foto;
    }

    @Transactional
    public ComentarioResponse comentarCliente(UUID token, UUID fotoId, ComentarioRequest request) {
        var agendamento = buscarAgendamentoPorToken(token);
        var foto = validarFotoPertencenteAoAgendamento(agendamento.getId(), fotoId);
        if (!foto.isVisivel() || (foto.getStatus() != StatusFoto.PUBLICADA && foto.getStatus() != StatusFoto.PAGA)) {
            throw new IllegalArgumentException("Esta foto não está disponível para comentários");
        }
        var comentario = FotoComentario.builder()
            .fotoId(foto.getId())
            .agendamentoId(agendamento.getId())
            .autorNome(normalizarNome(request.autorNome()))
            .mensagem(request.mensagem().trim())
            .origem(OrigemComentario.CLIENTE)
            .lida(false)
            .build();
        return ComentarioResponse.of(comentarioRepository.save(comentario));
    }

    @Transactional(readOnly = true)
    public List<ComentarioResponse> listarCliente(UUID token, UUID fotoId) {
        var agendamento = buscarAgendamentoPorToken(token);
        validarFotoPertencenteAoAgendamento(agendamento.getId(), fotoId);
        return comentarioRepository.findByFotoIdOrderByCreatedAtAsc(fotoId).stream()
            .map(ComentarioResponse::of)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ComentariosPorFotoResponse> listarAdmin(UUID agendamentoId) {
        var comentarios = comentarioRepository.findByAgendamentoIdOrderByCreatedAtAsc(agendamentoId);

        var porFoto = new LinkedHashMap<UUID, List<FotoComentario>>();
        for (var comentario : comentarios) {
            porFoto.computeIfAbsent(comentario.getFotoId(), k -> new ArrayList<>()).add(comentario);
        }

        return fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId).stream()
            .map(foto -> new ComentariosPorFotoResponse(
                FotoEnsaioResponse.of(foto),
                porFoto.getOrDefault(foto.getId(), List.of()).stream()
                    .map(ComentarioResponse::of)
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
        return ComentarioResponse.of(salvo);
    }

    @Transactional
    public void marcarLidos(UUID agendamentoId, UUID fotoId) {
        var comentarios = comentarioRepository.findByFotoIdOrderByCreatedAtAsc(fotoId).stream()
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