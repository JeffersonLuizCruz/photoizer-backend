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
import com.photoizer.crm.foto.event.FotoEdicaoPublicadaEvent;
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
 *
 * MODULITH: Publica eventos FotoEdicaoPublicadaEvent em vez de escrever
 * diretamente em FotoEnsaio/FotoEnsaioRepository. O listener no módulo foto
 * cria as FotoEnsaio.
 */
@Service
@Transactional
public class PublicacaoService {

    private static final Logger log = LoggerFactory.getLogger(PublicacaoService.class);

    private final EdicaoRepository edicaoRepository;
    private final FotoEdicaoRepository fotoEdicaoRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PublicacaoService(EdicaoRepository edicaoRepository,
                             FotoEdicaoRepository fotoEdicaoRepository,
                             AgendamentoRepository agendamentoRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.edicaoRepository = edicaoRepository;
        this.fotoEdicaoRepository = fotoEdicaoRepository;
        this.agendamentoRepository = agendamentoRepository;
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

        for (var fotoEdicao : fotosEditadas) {
            var fotoId = fotoEdicao.getId();
            eventPublisher.publishEvent(new FotoEdicaoPublicadaEvent(
                agendamentoId,
                fotoEdicao.getId(),
                fotoEdicao.getEditedFileName() != null ? fotoEdicao.getEditedFileName() : fotoEdicao.getRawFileName(),
                fotoEdicao.getEditedPath(),
                fotoId
            ));
        }

        eventPublisher.publishEvent(new FotosPublicadasEvent(agendamentoId, fotosEditadas.size(),
            FotosPublicadasEvent.TipoPublicacao.ECOMMERCE));

        avancarAgendamentoParaSelecao(agendamentoId);
    }

    private void publicarLoja(Edicao edicao, UUID agendamentoId, EdicaoMapper edicaoMapper) {
        if (edicao.getStatus() == StatusEdicao.AGUARDANDO_RAW || edicao.getStatus() == StatusEdicao.RAW_ENVIADOS) {
            throw new StatusEdicaoInvalidoException(
                "A edição precisa estar concluída para publicar na loja. Status atual: " + edicao.getStatus()
            );
        }

        eventPublisher.publishEvent(new FotosPublicadasEvent(agendamentoId, 0,
            FotosPublicadasEvent.TipoPublicacao.LOJA));

        avancarAgendamentoParaSelecao(agendamentoId);

        log.info("Evento de publicação na loja publicado para agendamento {}", agendamentoId);
    }

    private void avancarAgendamentoParaSelecao(UUID agendamentoId) {
        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new EdicaoNaoEncontradaException("Agendamento não encontrado: " + agendamentoId));
        if (agendamento.getStatus() == StatusAgendamento.SELECAO_DAS_FOTOS) {
            return;
        }
        agendamento.transicionarPara(StatusAgendamento.SELECAO_DAS_FOTOS);
        agendamentoRepository.save(agendamento);
    }
}
