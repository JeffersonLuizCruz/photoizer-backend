package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.ecommerce.exception.GaleriaEmEdicaoException;
import com.photoizer.crm.ecommerce.model.FaseGaleria;
import com.photoizer.crm.ecommerce.model.GaleriaEnsaio;
import com.photoizer.crm.ecommerce.model.ModoGaleria;
import com.photoizer.crm.ecommerce.model.StatusSelecao;
import com.photoizer.crm.ecommerce.repository.GaleriaEnsaioRepository;
import com.photoizer.crm.ecommerce.repository.SelecaoEnsaioItemRepository;
import com.photoizer.crm.ecommerce.repository.SelecaoEnsaioRepository;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.model.VersaoFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Fachada da configuracao comercial da galeria.
 *
 * <p>A ausencia de linha significa galeria legada. Nesse caso o modo padrao e
 * {@code DIRETO} e a fase {@code COMERCIALIZACAO}, preservando o comportamento
 * anterior a esta funcionalidade.
 */
@Service
@Transactional
public class GaleriaEnsaioService {

    private static final Logger log = LoggerFactory.getLogger(GaleriaEnsaioService.class);

    private static final List<StatusSelecao> STATUS_ATIVOS = List.of(
        StatusSelecao.ENVIADA, StatusSelecao.EM_EDICAO, StatusSelecao.CONCLUIDA);

    private final GaleriaEnsaioRepository galeriaRepository;
    private final SelecaoEnsaioRepository selecaoRepository;
    private final SelecaoEnsaioItemRepository selecaoItemRepository;
    private final FotoEnsaioRepository fotoRepository;
    private final ConfiguracaoService configuracaoService;

    public GaleriaEnsaioService(GaleriaEnsaioRepository galeriaRepository,
                                SelecaoEnsaioRepository selecaoRepository,
                                SelecaoEnsaioItemRepository selecaoItemRepository,
                                FotoEnsaioRepository fotoRepository,
                                ConfiguracaoService configuracaoService) {
        this.galeriaRepository = galeriaRepository;
        this.selecaoRepository = selecaoRepository;
        this.selecaoItemRepository = selecaoItemRepository;
        this.fotoRepository = fotoRepository;
        this.configuracaoService = configuracaoService;
    }

    @Transactional(readOnly = true)
    public GaleriaEnsaio obter(UUID agendamentoId) {
        return galeriaRepository.findById(agendamentoId)
            .orElseGet(() -> {
                var modo = modoPadrao();
                return GaleriaEnsaio.builder()
                    .agendamentoId(agendamentoId)
                    .modo(modo)
                    .fase(faseInicial(modo))
                    .build();
            });
    }

    public GaleriaEnsaio obterOuCriar(UUID agendamentoId) {
        return galeriaRepository.findById(agendamentoId)
            .orElseGet(() -> {
                var modo = modoPadrao();
                var nova = GaleriaEnsaio.builder()
                    .agendamentoId(agendamentoId)
                    .modo(modo)
                    .fase(faseInicial(modo))
                    .build();
                log.atInfo()
                    .addKeyValue("event_action", "galeria.criada")
                    .addKeyValue("agendamento_id", agendamentoId)
                    .addKeyValue("modo", nova.getModo())
                    .log("Configuração de galeria criada");
                return galeriaRepository.save(nova);
            });
    }

    public GaleriaEnsaio obterParaAtualizacao(UUID agendamentoId) {
        obterOuCriar(agendamentoId);
        return galeriaRepository.findByIdForUpdate(agendamentoId)
            .orElseThrow(() -> new IllegalStateException(
                "Galeria não encontrada após criação: " + agendamentoId));
    }

    public boolean podeAlterarModo(UUID agendamentoId) {        if (selecaoRepository.existsByAgendamentoIdAndStatusIn(agendamentoId, STATUS_ATIVOS)) {
            return false;
        }
        if (selecaoRepository.existsByAgendamentoIdAndStatusIn(
                agendamentoId, List.of(StatusSelecao.PUBLICADA))) {
            return false;
        }
        return !temVendaOuDownload(agendamentoId);
    }

    public GaleriaEnsaio alterarModo(UUID agendamentoId, ModoGaleria novoModo) {
        var galeria = obterParaAtualizacao(agendamentoId);
        if (selecaoRepository.existsByAgendamentoIdAndStatusIn(agendamentoId, STATUS_ATIVOS)) {
            throw new GaleriaEmEdicaoException("alterar o modo enquanto há seleção em andamento");
        }
        if (selecaoRepository.existsByAgendamentoIdAndStatusIn(
                agendamentoId, List.of(StatusSelecao.PUBLICADA))) {
            throw new GaleriaEmEdicaoException("alterar o modo após a publicação da edição");
        }
        if (temVendaOuDownload(agendamentoId)) {
            throw new GaleriaEmEdicaoException("alterar o modo após venda ou download");
        }
        galeria.setModo(novoModo);
        galeria.setFase(novoModo == ModoGaleria.COM_EDICAO
            ? FaseGaleria.SELECAO
            : FaseGaleria.COMERCIALIZACAO);
        galeria.setSelecaoAtualId(null);
        log.atInfo()
            .addKeyValue("event_action", "galeria.modo_alterado")
            .addKeyValue("agendamento_id", agendamentoId)
            .addKeyValue("modo", novoModo)
            .log("Modo da galeria alterado");
        return galeriaRepository.save(galeria);
    }

    /**
     * Define ou remove a foto de capa da galeria.
     *
     * <p>A versão atual da foto é congelada em {@code capaVersao}. Assim, uma
     * edição publicada depois não troca a capa automaticamente.
     */
    public GaleriaEnsaio definirCapa(UUID agendamentoId, UUID fotoId) {
        var galeria = obterParaAtualizacao(agendamentoId);

        if (fotoId == null) {
            galeria.setCapaFotoId(null);
            galeria.setCapaVersao(null);
            log.atInfo()
                .addKeyValue("event_action", "galeria.capa_removida")
                .addKeyValue("agendamento_id", agendamentoId)
                .log("Capa manual da galeria removida");
            return galeriaRepository.save(galeria);
        }

        var foto = fotoRepository.findById(fotoId)
            .orElseThrow(() -> new com.photoizer.crm.ecommerce.exception.FotoNaoEncontradaException(fotoId));
        if (!foto.getAgendamentoId().equals(agendamentoId)) {
            throw new com.photoizer.crm.ecommerce.exception.FotoIndisponivelException(
                "Foto não pertence a esta galeria");
        }
        if (!foto.isVisivel()
            || (foto.getStatus() != StatusFoto.PUBLICADA && foto.getStatus() != StatusFoto.PAGA)) {
            throw new com.photoizer.crm.ecommerce.exception.FotoIndisponivelException(
                "A foto de capa precisa estar visível e publicada");
        }
        if (galeria.getModo() == ModoGaleria.COM_EDICAO
            && galeria.getFase() == FaseGaleria.COMERCIALIZACAO) {
            var publicada = selecaoRepository
                .findFirstByAgendamentoIdAndStatusInOrderByNumeroDesc(
                    agendamentoId, List.of(StatusSelecao.PUBLICADA));
            var pertence = publicada
                .map(selecao -> selecaoItemRepository
                    .findBySelecaoIdAndFotoId(selecao.getId(), fotoId).isPresent())
                .orElse(false);
            if (!pertence) {
                throw new com.photoizer.crm.ecommerce.exception.FotoIndisponivelException(
                    "A foto de capa precisa fazer parte da seleção publicada");
            }
        }

        var versao = foto.getVersaoAtual();
        if (versao == VersaoFoto.EDITADA && foto.getEditadaPath() == null) {
            versao = VersaoFoto.ORIGINAL;
        }
        galeria.setCapaFotoId(fotoId);
        galeria.setCapaVersao(versao);
        log.atInfo()
            .addKeyValue("event_action", "galeria.capa_definida")
            .addKeyValue("agendamento_id", agendamentoId)
            .addKeyValue("foto_id", fotoId)
            .addKeyValue("versao", versao)
            .log("Capa manual da galeria definida");
        return galeriaRepository.save(galeria);
    }

    private boolean temVendaOuDownload(UUID agendamentoId) {
        var fotos = fotoRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId);
        return fotos.stream().anyMatch(f ->
            f.getDataDownload() != null
                || f.getCompraExtraId() != null
                || f.getStatus() == StatusFoto.PAGA
                || f.getStatus() == StatusFoto.AGUARDANDO_COMPROVANTE
                || f.getStatus() == StatusFoto.AGUARDANDO_CONFIRMACAO);
    }

    public void validarSelecaoPermitida(GaleriaEnsaio galeria) {
        if (!galeria.permiteSelecionar()) {
            throw new GaleriaEmEdicaoException("alterar a seleção");
        }
    }

    public void validarComercializacaoPermitida(GaleriaEnsaio galeria) {
        if (!galeria.permiteComercializar()) {
            throw new GaleriaEmEdicaoException("comprar ou baixar fotos");
        }
    }

    private FaseGaleria faseInicial(ModoGaleria modo) {
        return modo == ModoGaleria.COM_EDICAO
            ? FaseGaleria.SELECAO
            : FaseGaleria.COMERCIALIZACAO;
    }

    private ModoGaleria modoPadrao() {
        var valor = configuracaoService.getValor(ConfigKey.GALERIA_MODO_PADRAO);
        try {
            return ModoGaleria.valueOf(valor == null ? "DIRETO" : valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.atWarn()
                .addKeyValue("event_action", "galeria.modo_padrao_invalido")
                .addKeyValue("valor", valor)
                .log("Modo de galeria inválido na configuração; usando DIRETO");
            return ModoGaleria.DIRETO;
        }
    }
}
