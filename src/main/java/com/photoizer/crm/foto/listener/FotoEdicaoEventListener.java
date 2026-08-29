package com.photoizer.crm.foto.listener;

import com.photoizer.crm.edicao.event.FotosPublicadasEvent;
import com.photoizer.crm.foto.event.FotoEdicaoPublicadaEvent;
import com.photoizer.crm.foto.event.FotoEdicaoRemovidaEvent;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import com.photoizer.crm.foto.service.FotoProcessingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;

/**
 * PATTERN: Event Listener (Modulith)
 * Escuta eventos do módulo edicao e cria/remove/atualiza FotoEnsaio.
 * Motivo: eliminar escrita cross-module — o edicao não deve importar
 * FotoEnsaio/FotoEnsaioRepository diretamente.
 *
 * Substitui a lógica que existia em:
 * - PublicacaoService.publicarEcommerce() — criação de FotoEnsaio
 * - PublicacaoService.publicarLoja() — mudança de status INEDITA→PUBLICADA
 * - EdicaoRevisaoService.criarOuAtualizarFotoEnsaio() — criação de FotoEnsaio
 * - EdicaoRevisaoService.removerFotoEnsaioSeInedita() — exclusão de FotoEnsaio
 */
@Component
public class FotoEdicaoEventListener {

    private static final Logger log = LoggerFactory.getLogger(FotoEdicaoEventListener.class);

    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final FotoProcessingHelper fotoProcessingHelper;

    public FotoEdicaoEventListener(FotoEnsaioRepository fotoEnsaioRepository,
                                   FotoProcessingHelper fotoProcessingHelper) {
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.fotoProcessingHelper = fotoProcessingHelper;
    }

    @EventListener
    @Transactional
    public void handleFotoEdicaoPublicada(FotoEdicaoPublicadaEvent event) {
        log.info("Recebido FotoEdicaoPublicadaEvent: agendamento={}, fotoEdicao={}",
            event.agendamentoId(), event.fotoEdicaoId());

        var existing = fotoEnsaioRepository.findByFotoEdicaoId(event.fotoEdicaoId());
        if (existing.isPresent()) {
            log.info("FotoEnsaio já existe para fotoEdicao={}, ignorando", event.fotoEdicaoId());
            return;
        }

        if (event.originalPath() == null || event.originalPath().isBlank()) {
            log.warn("FotoEdicaoPublicadaEvent com originalPath nulo/vazio para fotoEdicao={}, ignorando",
                event.fotoEdicaoId());
            return;
        }

        var original = Path.of(event.originalPath());
        var targetDir = original.getParent();
        var processada = fotoProcessingHelper.processar(original, targetDir, event.fotoId());

        var count = fotoEnsaioRepository.findMaxOrdemByAgendamentoId(event.agendamentoId()) + 1;
        var foto = FotoEnsaio.builder()
            .agendamentoId(event.agendamentoId())
            .fotoEdicaoId(event.fotoEdicaoId())
            .fileName(event.fileName())
            .originalPath(event.originalPath())
            .watermarkedPath(processada.watermarkedPath())
            .thumbPath(processada.thumbPath())
            .ordem(count)
            .status(StatusFoto.INEDITA)
            .selecionadaPacote(false)
            .visivel(true)
            .build();

        fotoEnsaioRepository.save(foto);
    }

    @EventListener
    @Transactional
    public void handleFotoEdicaoRemovida(FotoEdicaoRemovidaEvent event) {
        log.info("Recebido FotoEdicaoRemovidaEvent: fotoEdicao={}", event.fotoEdicaoId());
        fotoEnsaioRepository.findByFotoEdicaoId(event.fotoEdicaoId()).ifPresent(ensaio -> {
            if (ensaio.getStatus() == StatusFoto.INEDITA) {
                fotoEnsaioRepository.delete(ensaio);
            }
        });
    }

    @EventListener
    @Transactional
    public void handleFotosPublicadas(FotosPublicadasEvent event) {
        if (event.tipo() != FotosPublicadasEvent.TipoPublicacao.LOJA) {
            return;
        }
        log.info("Recebido FotosPublicadasEvent (LOJA): agendamento={}", event.agendamentoId());
        var fotosIneditas = fotoEnsaioRepository.findByAgendamentoIdAndStatusOrderByOrdemAsc(
            event.agendamentoId(), StatusFoto.INEDITA);
        for (var foto : fotosIneditas) {
            foto.setStatus(StatusFoto.PUBLICADA);
        }
        fotoEnsaioRepository.saveAll(fotosIneditas);
        log.info("Fotos INEDITA→PUBLICADA na loja para agendamento {}: {} fotos",
            event.agendamentoId(), fotosIneditas.size());
    }
}
