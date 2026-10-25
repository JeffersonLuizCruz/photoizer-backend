package com.photoizer.crm.ecommerce.listener;

import com.photoizer.crm.ecommerce.repository.FavoritoRepository;
import com.photoizer.crm.ecommerce.repository.FotoComentarioRepository;
import com.photoizer.crm.ecommerce.repository.ItemCarrinhoRepository;
import com.photoizer.crm.foto.event.FotosRemovidasEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limpa referências do ecommerce quando fotos são removidas, evitando
 * carrinhos, favoritos e comentários órfãos.
 */
@Component
public class FotosRemovidasListener {

    private static final Logger log = LoggerFactory.getLogger(FotosRemovidasListener.class);

    private final FavoritoRepository favoritoRepository;
    private final ItemCarrinhoRepository itemCarrinhoRepository;
    private final FotoComentarioRepository fotoComentarioRepository;

    public FotosRemovidasListener(FavoritoRepository favoritoRepository,
                                  ItemCarrinhoRepository itemCarrinhoRepository,
                                  FotoComentarioRepository fotoComentarioRepository) {
        this.favoritoRepository = favoritoRepository;
        this.itemCarrinhoRepository = itemCarrinhoRepository;
        this.fotoComentarioRepository = fotoComentarioRepository;
    }

    @EventListener
    @Transactional
    public void handle(FotosRemovidasEvent event) {
        if (event.fotoIds().isEmpty()) {
            return;
        }
        favoritoRepository.deleteByFotoIdIn(event.fotoIds());
        itemCarrinhoRepository.deleteByFotoIdIn(event.fotoIds());
        fotoComentarioRepository.deleteByFotoIdIn(event.fotoIds());

        log.atInfo()
            .addKeyValue("event_action", "ecommerce.referencias_fotos_removidas")
            .addKeyValue("agendamento_id", event.agendamentoId())
            .addKeyValue("quantidade", event.fotoIds().size())
            .log("Referências de fotos removidas limpas no ecommerce");
    }
}
