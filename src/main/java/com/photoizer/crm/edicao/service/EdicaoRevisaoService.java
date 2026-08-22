package com.photoizer.crm.edicao.service;

import com.photoizer.crm.edicao.api.EdicaoMapper;
import com.photoizer.crm.edicao.api.FotoEdicaoResponse;
import com.photoizer.crm.edicao.api.RevisaoRequest;
import com.photoizer.crm.edicao.exception.EdicaoNaoEncontradaException;
import com.photoizer.crm.edicao.exception.FotoEdicaoNaoEncontradaException;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.edicao.repository.EdicaoRepository;
import com.photoizer.crm.edicao.repository.FotoEdicaoRepository;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Command Pattern — encapsula operação de revisão com efeitos colaterais controlados.
 * Extraído de EdicaoService para decompor a god class.
 */
@Service
@Transactional
public class EdicaoRevisaoService {

    private static final Logger log = LoggerFactory.getLogger(EdicaoRevisaoService.class);

    private final EdicaoRepository edicaoRepository;
    private final FotoEdicaoRepository fotoEdicaoRepository;
    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final FotoEdicaoProcessor fotoEdicaoProcessor;

    public EdicaoRevisaoService(EdicaoRepository edicaoRepository,
                                FotoEdicaoRepository fotoEdicaoRepository,
                                FotoEnsaioRepository fotoEnsaioRepository,
                                FotoEdicaoProcessor fotoEdicaoProcessor) {
        this.edicaoRepository = edicaoRepository;
        this.fotoEdicaoRepository = fotoEdicaoRepository;
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.fotoEdicaoProcessor = fotoEdicaoProcessor;
    }

    public FotoEdicaoResponse revisarFoto(UUID fotoId, RevisaoRequest request, EdicaoMapper edicaoMapper) {
        var foto = fotoEdicaoRepository.findById(fotoId)
            .orElseThrow(() -> new FotoEdicaoNaoEncontradaException("Foto não encontrada: " + fotoId));

        if (request.aprovado() != null) {
            foto.setAprovado(request.aprovado());
        }
        if (request.comentario() != null) {
            foto.setComentario(request.comentario());
        }

        fotoEdicaoRepository.save(foto);

        if (Boolean.TRUE.equals(request.aprovado()) && foto.getEditedPath() != null) {
            criarOuAtualizarFotoEnsaio(foto, fotoId);
        } else {
            removerFotoEnsaioSeInedita(fotoId);
        }

        return edicaoMapper.toResponse(foto);
    }

    private void criarOuAtualizarFotoEnsaio(com.photoizer.crm.edicao.model.FotoEdicao foto, UUID fotoId) {
        var existing = fotoEnsaioRepository.findByFotoEdicaoId(fotoId);
        if (existing.isEmpty()) {
            var edicao = edicaoRepository.findById(foto.getEdicaoId())
                .orElseThrow(() -> new EdicaoNaoEncontradaException("Processo de edição não encontrado"));

            var editedPath = java.nio.file.Path.of(foto.getEditedPath());
            var processada = fotoEdicaoProcessor.processar(editedPath, fotoId);

            var count = fotoEnsaioRepository.countByAgendamentoId(edicao.getAgendamentoId());
            var ensaio = FotoEnsaio.builder()
                .agendamentoId(edicao.getAgendamentoId())
                .fotoEdicaoId(fotoId)
                .fileName(foto.getEditedFileName() != null ? foto.getEditedFileName() : foto.getRawFileName())
                .originalPath(foto.getEditedPath())
                .watermarkedPath(processada.watermarkedPath())
                .thumbPath(processada.thumbPath())
                .ordem((int) count)
                .status(StatusFoto.INEDITA)
                .selecionadaPacote(false)
                .visivel(true)
                .build();
            fotoEnsaioRepository.save(ensaio);
        }
    }

    private void removerFotoEnsaioSeInedita(UUID fotoId) {
        fotoEnsaioRepository.findByFotoEdicaoId(fotoId).ifPresent(ensaio -> {
            if (ensaio.getStatus() == StatusFoto.INEDITA) {
                fotoEnsaioRepository.delete(ensaio);
            }
        });
    }
}
