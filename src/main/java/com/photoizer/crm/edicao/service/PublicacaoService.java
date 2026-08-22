package com.photoizer.crm.edicao.service;

import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.edicao.api.EdicaoMapper;
import com.photoizer.crm.edicao.api.EdicaoResponse;
import com.photoizer.crm.edicao.event.FotosPublicadasEvent;
import com.photoizer.crm.edicao.exception.EdicaoNaoEncontradaException;
import com.photoizer.crm.edicao.exception.FotoEdicaoNaoEncontradaException;
import com.photoizer.crm.edicao.exception.StatusEdicaoInvalidoException;
import com.photoizer.crm.edicao.model.Edicao;
import com.photoizer.crm.edicao.model.StatusEdicao;
import com.photoizer.crm.edicao.model.StatusFotoEdicao;
import com.photoizer.crm.edicao.repository.EdicaoRepository;
import com.photoizer.crm.edicao.repository.FotoEdicaoRepository;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Strategy Pattern — unifica dois fluxos de publicação (ecommerce/loja)
 * em uma única operação com comportamento variável.
 * Elimina duplicação entre publicarNoEcommerce e publicarLoja.
 */
@Service
@Transactional
public class PublicacaoService {

    private static final Logger log = LoggerFactory.getLogger(PublicacaoService.class);

    private final EdicaoRepository edicaoRepository;
    private final FotoEdicaoRepository fotoEdicaoRepository;
    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final FotoEdicaoProcessor fotoEdicaoProcessor;
    private final ApplicationEventPublisher eventPublisher;

    public PublicacaoService(EdicaoRepository edicaoRepository,
                             FotoEdicaoRepository fotoEdicaoRepository,
                             FotoEnsaioRepository fotoEnsaioRepository,
                             AgendamentoRepository agendamentoRepository,
                             FotoEdicaoProcessor fotoEdicaoProcessor,
                             ApplicationEventPublisher eventPublisher) {
        this.edicaoRepository = edicaoRepository;
        this.fotoEdicaoRepository = fotoEdicaoRepository;
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.fotoEdicaoProcessor = fotoEdicaoProcessor;
        this.eventPublisher = eventPublisher;
    }

    public enum PublicacaoTipo {
        ECOMMERCE,
        LOJA
    }

    public EdicaoResponse publicar(UUID agendamentoId, PublicacaoTipo tipo, EdicaoMapper edicaoMapper) {
        var edicao = edicaoRepository.findByAgendamentoId(agendamentoId)
            .orElseThrow(() -> new EdicaoNaoEncontradaException("Processo de edição não encontrado"));

        if (tipo == PublicacaoTipo.ECOMMERCE) {
            publicarEcommerce(edicao, agendamentoId, edicaoMapper);
        } else {
            publicarLoja(edicao, agendamentoId, edicaoMapper);
        }

        var totalRaw = fotoEdicaoRepository.countByEdicaoIdAndStatus(edicao.getId(), StatusFotoEdicao.RAW);
        var totalEditadas = fotoEdicaoRepository.countByEdicaoIdAndStatus(edicao.getId(), StatusFotoEdicao.EDITADO);
        return edicaoMapper.toResponse(edicao, totalRaw, totalEditadas);
    }

    private void publicarEcommerce(Edicao edicao, UUID agendamentoId, EdicaoMapper edicaoMapper) {
        if (edicao.getStatus() != StatusEdicao.EDICAO_CONCLUIDA) {
            throw new StatusEdicaoInvalidoException(
                "A edição precisa estar concluída para publicar no ecommerce. Status atual: " + edicao.getStatus()
            );
        }

        var fotosEditadas = fotoEdicaoRepository.findByEdicaoIdAndStatus(edicao.getId(), StatusFotoEdicao.EDITADO);

        if (fotosEditadas.isEmpty()) {
            throw new FotoEdicaoNaoEncontradaException("Nenhuma foto editada encontrada para publicar.");
        }

        var count = fotoEnsaioRepository.countByAgendamentoId(agendamentoId);

        for (int i = 0; i < fotosEditadas.size(); i++) {
            var fotoEdicao = fotosEditadas.get(i);
            var editedPath = java.nio.file.Path.of(fotoEdicao.getEditedPath());

            var processada = fotoEdicaoProcessor.processar(editedPath, fotoEdicao.getId());

            var fotoEnsaio = FotoEnsaio.builder()
                .agendamentoId(agendamentoId)
                .fileName(fotoEdicao.getEditedFileName() != null ? fotoEdicao.getEditedFileName() : fotoEdicao.getRawFileName())
                .originalPath(fotoEdicao.getEditedPath())
                .watermarkedPath(processada.watermarkedPath())
                .thumbPath(processada.thumbPath())
                .ordem(count + i)
                .status(StatusFoto.PUBLICADA)
                .selecionadaPacote(false)
                .visivel(true)
                .build();

            fotoEnsaioRepository.save(fotoEnsaio);
        }

        eventPublisher.publishEvent(new FotosPublicadasEvent(agendamentoId, fotosEditadas.size()));

        avancarAgendamentoParaSelecao(agendamentoId);
    }

    private void publicarLoja(Edicao edicao, UUID agendamentoId, EdicaoMapper edicaoMapper) {
        if (edicao.getStatus() == StatusEdicao.AGUARDANDO_RAW || edicao.getStatus() == StatusEdicao.RAW_ENVIADOS) {
            throw new StatusEdicaoInvalidoException(
                "A edição precisa estar concluída para publicar na loja. Status atual: " + edicao.getStatus()
            );
        }

        var fotosIneditas = fotoEnsaioRepository.findByAgendamentoIdAndStatusOrderByOrdemAsc(
            agendamentoId, StatusFoto.INEDITA);

        if (fotosIneditas.isEmpty()) {
            var fotosPublicadas = fotoEnsaioRepository.findByAgendamentoIdAndStatusOrderByOrdemAsc(
                agendamentoId, StatusFoto.PUBLICADA);
            if (!fotosPublicadas.isEmpty()) {
                avancarAgendamentoParaSelecao(agendamentoId);
                return;
            }
            throw new FotoEdicaoNaoEncontradaException("Nenhuma foto aprovada encontrada para publicar.");
        }

        for (var foto : fotosIneditas) {
            foto.setStatus(StatusFoto.PUBLICADA);
        }
        fotoEnsaioRepository.saveAll(fotosIneditas);

        avancarAgendamentoParaSelecao(agendamentoId);

        eventPublisher.publishEvent(new FotosPublicadasEvent(agendamentoId, fotosIneditas.size()));

        log.info("Fotos publicadas na loja para agendamento {}: {} fotos", agendamentoId, fotosIneditas.size());
    }

    private void avancarAgendamentoParaSelecao(UUID agendamentoId) {
        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new EdicaoNaoEncontradaException("Agendamento não encontrado: " + agendamentoId));
        agendamento.setStatus(StatusAgendamento.SELECAO_DAS_FOTOS);
        agendamentoRepository.save(agendamento);
    }
}
