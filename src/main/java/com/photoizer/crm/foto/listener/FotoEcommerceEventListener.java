package com.photoizer.crm.foto.listener;

import com.photoizer.crm.ecommerce.event.CompraExtraCanceladaEvent;
import com.photoizer.crm.ecommerce.event.CompraExtraFotosAssociadasEvent;
import com.photoizer.crm.ecommerce.event.CompraExtraPagaEvent;
import com.photoizer.crm.ecommerce.event.FotoDownloadEvent;
import com.photoizer.crm.ecommerce.event.FotosSelecionadasEvent;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * PATTERN: Event Listener (Modulith)
 * Escuta eventos do módulo ecommerce e atualiza entidades FotoEnsaio.
 * Motivo: desacoplar o módulo ecommerce do módulo foto - o ecommerce
 * não deve escrever diretamente em FotoEnsaio, apenas publicar eventos.
 */
@Component
public class FotoEcommerceEventListener {

    private static final Logger log = LoggerFactory.getLogger(FotoEcommerceEventListener.class);

    private final FotoEnsaioRepository fotoEnsaioRepository;

    public FotoEcommerceEventListener(FotoEnsaioRepository fotoEnsaioRepository) {
        this.fotoEnsaioRepository = fotoEnsaioRepository;
    }

    @EventListener
    @Transactional
    public void handleFotosSelecionadas(FotosSelecionadasEvent event) {
        log.info("Recebido FotosSelecionadasEvent: agendamento={}, fotos={}, selecionada={}",
            event.agendamentoId(), event.fotoIds().size(), event.selecionada());
        var fotos = fotoEnsaioRepository.findAllById(event.fotoIds());
        for (var foto : fotos) {
            if (foto.getAgendamentoId().equals(event.agendamentoId())) {
                foto.setSelecionadaPacote(event.selecionada());
            }
        }
        fotoEnsaioRepository.saveAll(fotos);
    }

    @EventListener
    @Transactional
    public void handleCompraExtraFotosAssociadas(CompraExtraFotosAssociadasEvent event) {
        log.info("Recebido CompraExtraFotosAssociadasEvent: compra={}, fotos={}",
            event.compraExtraId(), event.fotoIds().size());
        var fotos = fotoEnsaioRepository.findAllById(event.fotoIds());
        for (var foto : fotos) {
            if (foto.getAgendamentoId().equals(event.agendamentoId())) {
                foto.setCompraExtraId(event.compraExtraId());
            }
        }
        fotoEnsaioRepository.saveAll(fotos);
    }

    @EventListener
    @Transactional
    public void handleCompraExtraCancelada(CompraExtraCanceladaEvent event) {
        log.info("Recebido CompraExtraCanceladaEvent: compra={}", event.compraExtraId());
        var fotos = fotoEnsaioRepository.findByCompraExtraId(event.compraExtraId());
        for (var foto : fotos) {
            foto.setCompraExtraId(null);
            foto.setStatus(StatusFoto.PUBLICADA);
            foto.setVisivel(true);
        }
        fotoEnsaioRepository.saveAll(fotos);
    }

    @EventListener
    @Transactional
    public void handleCompraExtraPaga(CompraExtraPagaEvent event) {
        log.info("Recebido CompraExtraPagaEvent: compra={}", event.compraExtraId());
        var fotos = fotoEnsaioRepository.findByCompraExtraId(event.compraExtraId());
        for (var foto : fotos) {
            foto.setStatus(StatusFoto.PAGA);
        }
        fotoEnsaioRepository.saveAll(fotos);
    }

    @EventListener
    @Transactional
    public void handleFotoDownload(FotoDownloadEvent event) {
        log.info("Recebido FotoDownloadEvent: fotos={}", event.fotoIds().size());
        var fotos = fotoEnsaioRepository.findAllById(event.fotoIds());
        var now = LocalDateTime.now();
        for (var foto : fotos) {
            foto.setDataDownload(now);
        }
        fotoEnsaioRepository.saveAll(fotos);
    }
}
