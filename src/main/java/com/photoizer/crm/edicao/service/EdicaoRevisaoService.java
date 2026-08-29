package com.photoizer.crm.edicao.service;

import com.photoizer.crm.edicao.api.EdicaoMapper;
import com.photoizer.crm.edicao.api.FotoEdicaoResponse;
import com.photoizer.crm.edicao.api.RevisaoRequest;
import com.photoizer.crm.edicao.exception.EdicaoNaoEncontradaException;
import com.photoizer.crm.edicao.exception.FotoEdicaoNaoEncontradaException;
import com.photoizer.crm.edicao.repository.EdicaoRepository;
import com.photoizer.crm.edicao.repository.FotoEdicaoRepository;
import com.photoizer.crm.foto.event.FotoEdicaoPublicadaEvent;
import com.photoizer.crm.foto.event.FotoEdicaoRemovidaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Command Pattern — encapsula operação de revisão com efeitos colaterais controlados.
 * Extraído de EdicaoService para decompor a god class.
 *
 * MODULITH: Publica eventos FotoEdicaoPublicadaEvent/FotoEdicaoRemovidaEvent
 * em vez de escrever diretamente em FotoEnsaio/FotoEnsaioRepository.
 */
@Service
@Transactional
public class EdicaoRevisaoService {

    private static final Logger log = LoggerFactory.getLogger(EdicaoRevisaoService.class);

    private final EdicaoRepository edicaoRepository;
    private final FotoEdicaoRepository fotoEdicaoRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EdicaoRevisaoService(EdicaoRepository edicaoRepository,
                                FotoEdicaoRepository fotoEdicaoRepository,
                                ApplicationEventPublisher eventPublisher) {
        this.edicaoRepository = edicaoRepository;
        this.fotoEdicaoRepository = fotoEdicaoRepository;
        this.eventPublisher = eventPublisher;
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
            criarFotoEnsaioViaEvento(foto, fotoId);
        } else if (Boolean.FALSE.equals(request.aprovado())) {
            removerFotoEnsaioViaEvento(fotoId);
        }

        return edicaoMapper.toResponse(foto);
    }

    private void criarFotoEnsaioViaEvento(com.photoizer.crm.edicao.model.FotoEdicao foto, UUID fotoId) {
        var edicao = edicaoRepository.findById(foto.getEdicaoId())
            .orElseThrow(() -> new EdicaoNaoEncontradaException("Processo de edição não encontrado"));

        eventPublisher.publishEvent(new FotoEdicaoPublicadaEvent(
            edicao.getAgendamentoId(),
            fotoId,
            foto.getEditedFileName() != null ? foto.getEditedFileName() : foto.getRawFileName(),
            foto.getEditedPath(),
            fotoId
        ));
    }

    private void removerFotoEnsaioViaEvento(UUID fotoId) {
        eventPublisher.publishEvent(new FotoEdicaoRemovidaEvent(fotoId));
    }
}
