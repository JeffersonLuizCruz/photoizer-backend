package com.photoizer.crm.ecommerce.listener;

import com.photoizer.crm.ecommerce.service.GaleriaEnsaioService;
import com.photoizer.crm.foto.event.FotosGaleriaPublicadasEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantem a data da primeira publicacao da galeria alinhada ao modulo foto.
 * O modulo foto publica o evento sem conhecer a configuracao comercial.
 */
@Component
public class GaleriaPublicacaoListener {

    private final GaleriaEnsaioService galeriaEnsaioService;

    public GaleriaPublicacaoListener(GaleriaEnsaioService galeriaEnsaioService) {
        this.galeriaEnsaioService = galeriaEnsaioService;
    }

    @EventListener
    @Transactional
    public void handleFotosPublicadas(FotosGaleriaPublicadasEvent event) {
        galeriaEnsaioService.registrarPublicacao(event.agendamentoId(), event.publicadoEm());
    }
}
