package com.photoizer.crm.foto.listener;

import com.photoizer.crm.ecommerce.event.CompraExtraCanceladaEvent;
import com.photoizer.crm.ecommerce.event.CompraExtraFotosAssociadasEvent;
import com.photoizer.crm.ecommerce.event.CompraExtraPagaEvent;
import com.photoizer.crm.ecommerce.event.EdicaoPublicadaEvent;
import com.photoizer.crm.ecommerce.event.FotoDownloadEvent;
import com.photoizer.crm.ecommerce.event.FotosSelecionadasEvent;
import com.photoizer.crm.ecommerce.event.SelecaoCanceladaEvent;
import com.photoizer.crm.ecommerce.event.SelecaoEnviadaEvent;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.model.VersaoFoto;
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
        log.atInfo()
            .addKeyValue("event_action", "foto.selecao_atualizada")
            .addKeyValue("agendamento_id", event.agendamentoId())
            .addKeyValue("quantidade", event.fotoIds().size())
            .addKeyValue("selecionada", event.selecionada())
            .log("Seleção de fotos recebida");
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
    public void handleSelecaoEnviada(SelecaoEnviadaEvent event) {
        log.atInfo()
            .addKeyValue("event_action", "foto.selecao_enviada")
            .addKeyValue("agendamento_id", event.agendamentoId())
            .addKeyValue("selecao_id", event.selecaoId())
            .addKeyValue("quantidade", event.fotoIds().size())
            .log("Fotos reservadas para edição");
        var fotos = fotoEnsaioRepository.findAllById(event.fotoIds());
        for (var foto : fotos) {
            if (foto.getAgendamentoId().equals(event.agendamentoId())) {
                foto.setEmSelecaoEdicao(true);
            }
        }
        fotoEnsaioRepository.saveAll(fotos);
    }

    @EventListener
    @Transactional
    public void handleSelecaoCancelada(SelecaoCanceladaEvent event) {
        log.atInfo()
            .addKeyValue("event_action", "foto.selecao_cancelada")
            .addKeyValue("agendamento_id", event.agendamentoId())
            .addKeyValue("selecao_id", event.selecaoId())
            .log("Fotos liberadas após cancelamento da seleção");
        var fotos = fotoEnsaioRepository.findAllById(event.fotoIds());
        for (var foto : fotos) {
            if (foto.getAgendamentoId().equals(event.agendamentoId())) {
                foto.setEmSelecaoEdicao(false);
            }
        }
        fotoEnsaioRepository.saveAll(fotos);
    }

    @EventListener
    @Transactional
    public void handleEdicaoPublicada(EdicaoPublicadaEvent event) {
        log.atInfo()
            .addKeyValue("event_action", "foto.edicao_publicada")
            .addKeyValue("agendamento_id", event.agendamentoId())
            .addKeyValue("selecao_id", event.selecaoId())
            .addKeyValue("quantidade", event.fotoIds().size())
            .log("Versão editada publicada na galeria");
        var fotos = fotoEnsaioRepository.findAllById(event.fotoIds());
        for (var foto : fotos) {
            if (foto.getAgendamentoId().equals(event.agendamentoId())) {
                if (foto.getEditadaPath() != null) {
                    foto.setVersaoAtual(VersaoFoto.EDITADA);
                }
                // A seleção foi publicada: a foto deixa de estar "em edição".
                // Sem isso, uploads, publicação, exclusão e renumeração ficam
                // bloqueados para sempre depois da primeira publicação.
                foto.setEmSelecaoEdicao(false);
            }
        }
        fotoEnsaioRepository.saveAll(fotos);
    }

    @EventListener
    @Transactional
    public void handleCompraExtraFotosAssociadas(CompraExtraFotosAssociadasEvent event) {
        log.atInfo()
            .addKeyValue("event_action", "foto.compra_associada")
            .addKeyValue("compra_id", event.compraExtraId())
            .addKeyValue("agendamento_id", event.agendamentoId())
            .addKeyValue("quantidade", event.fotoIds().size())
            .log("Fotos associadas à compra extra");
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
        log.atInfo()
            .addKeyValue("event_action", "foto.compra_cancelada")
            .addKeyValue("compra_id", event.compraExtraId())
            .log("Fotos liberadas por cancelamento da compra extra");
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
        log.atInfo()
            .addKeyValue("event_action", "foto.compra_paga")
            .addKeyValue("compra_id", event.compraExtraId())
            .log("Fotos liberadas por pagamento da compra extra");
        var fotos = fotoEnsaioRepository.findByCompraExtraId(event.compraExtraId());
        for (var foto : fotos) {
            foto.setStatus(StatusFoto.PAGA);
        }
        fotoEnsaioRepository.saveAll(fotos);
    }

    @EventListener
    @Transactional
    public void handleFotoDownload(FotoDownloadEvent event) {
        log.atInfo()
            .addKeyValue("event_action", "foto.download_registrado")
            .addKeyValue("quantidade", event.fotoIds().size())
            .log("Download de fotos registrado");
        var fotos = fotoEnsaioRepository.findAllById(event.fotoIds());
        var now = LocalDateTime.now();
        for (var foto : fotos) {
            foto.setDataDownload(now);
        }
        fotoEnsaioRepository.saveAll(fotos);
    }
}
