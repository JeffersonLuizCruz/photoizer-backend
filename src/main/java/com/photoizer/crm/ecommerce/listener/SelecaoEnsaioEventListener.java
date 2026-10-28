package com.photoizer.crm.ecommerce.listener;

import com.photoizer.crm.ecommerce.service.SelecaoEnsaioService;
import com.photoizer.crm.foto.event.FotoEditadaEvent;
import com.photoizer.crm.foto.event.FotoReeditadaEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantém o snapshot da seleção alinhado ao upload da versão editada.
 * O módulo foto publica o evento sem conhecer as regras de seleção.
 */
@Component
public class SelecaoEnsaioEventListener {

    private final SelecaoEnsaioService selecaoEnsaioService;

    public SelecaoEnsaioEventListener(SelecaoEnsaioService selecaoEnsaioService) {
        this.selecaoEnsaioService = selecaoEnsaioService;
    }

    @EventListener
    @Transactional
    public void handleFotoEditada(FotoEditadaEvent event) {
        selecaoEnsaioService.registrarFotoEditada(event.agendamentoId(), event.fotoId());
    }

    @EventListener
    @Transactional
    public void handleFotoReeditada(FotoReeditadaEvent event) {
        selecaoEnsaioService.registrarReedicao(
            event.agendamentoId(), event.fotoId(), event.editadaEm());
    }
}
