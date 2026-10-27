package com.photoizer.crm.ecommerce.listener;

import com.photoizer.crm.ecommerce.repository.FavoritoRepository;
import com.photoizer.crm.ecommerce.repository.FotoComentarioRepository;
import com.photoizer.crm.ecommerce.repository.GaleriaEnsaioRepository;
import com.photoizer.crm.ecommerce.repository.ItemCarrinhoRepository;
import com.photoizer.crm.ecommerce.service.GaleriaCapaService;
import com.photoizer.crm.foto.event.FotosRemovidasEvent;
import com.photoizer.crm.foto.event.GaleriaArquivadaEvent;
import com.photoizer.crm.foto.service.FotoPreviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Limpa referências do ecommerce quando fotos são removidas ou quando uma
 * galeria é arquivada, evitando carrinhos, favoritos e comentários órfãos.
 */
@Component
public class FotosRemovidasListener {

    private static final Logger log = LoggerFactory.getLogger(FotosRemovidasListener.class);

    private final FavoritoRepository favoritoRepository;
    private final ItemCarrinhoRepository itemCarrinhoRepository;
    private final FotoComentarioRepository fotoComentarioRepository;
    private final GaleriaEnsaioRepository galeriaRepository;
    private final GaleriaCapaService capaService;
    private final FotoPreviewService fotoPreviewService;

    public FotosRemovidasListener(FavoritoRepository favoritoRepository,
                                  ItemCarrinhoRepository itemCarrinhoRepository,
                                  FotoComentarioRepository fotoComentarioRepository,
                                  GaleriaEnsaioRepository galeriaRepository,
                                  GaleriaCapaService capaService,
                                  FotoPreviewService fotoPreviewService) {
        this.favoritoRepository = favoritoRepository;
        this.itemCarrinhoRepository = itemCarrinhoRepository;
        this.fotoComentarioRepository = fotoComentarioRepository;
        this.galeriaRepository = galeriaRepository;
        this.capaService = capaService;
        this.fotoPreviewService = fotoPreviewService;
    }

    @EventListener
    @Transactional
    public void handle(FotosRemovidasEvent event) {
        limpar(event.agendamentoId(), event.fotoIds(), "ecommerce.referencias_fotos_removidas");
    }

    @EventListener
    @Transactional
    public void handleArquivada(GaleriaArquivadaEvent event) {
        limpar(event.agendamentoId(), event.fotoIds(), "ecommerce.referencias_galeria_arquivada");
    }

    private void limpar(UUID agendamentoId, List<UUID> fotoIds, String acao) {
        if (fotoIds == null || fotoIds.isEmpty()) {
            return;
        }
        favoritoRepository.deleteByFotoIdIn(fotoIds);
        itemCarrinhoRepository.deleteByFotoIdIn(fotoIds);
        fotoComentarioRepository.deleteByFotoIdIn(fotoIds);

        galeriaRepository.findById(agendamentoId).ifPresent(galeria -> {
            var capaRemovida = galeria.getCapaFotoId() != null
                && fotoIds.contains(galeria.getCapaFotoId());
            if (capaRemovida || (galeria.getCapaFotoId() == null && galeria.getCapaVersao() != null)) {
                galeria.setCapaFotoId(null);
                galeria.setCapaVersao(null);
                galeriaRepository.save(galeria);
            }
        });
        capaService.removerHeroes(agendamentoId, fotoIds);
        fotoPreviewService.removerLegados(agendamentoId, fotoIds);

        log.atInfo()
            .addKeyValue("event_action", acao)
            .addKeyValue("agendamento_id", agendamentoId)
            .addKeyValue("quantidade", fotoIds.size())
            .log("Referências de fotos limpas no ecommerce");
    }
}
