package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.ecommerce.exception.GaleriaArquivadaException;
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
import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;
import com.photoizer.crm.shared.processing.QualidadePreview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fachada da configuracao comercial da galeria.
 *
 * <p>A ausencia de linha significa galeria nova (ou ainda nao configurada).
 * O modo padrao e sempre {@code COM_EDICAO}, com fase {@code SELECAO}. O fluxo
 * legado {@code DIRETO} foi desativado e so permanece legivel para linhas
 * antigas que nao puderam ser migradas.
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

    /**
     * Carrega as galerias de varios agendamentos de uma vez. Agendamentos sem
     * linha configurada recebem o modo padrao vigente e a fase inicial em
     * memoria, sem gravar nada. Usado pela Central de Galerias para evitar N+1.
     */
    @Transactional(readOnly = true)
    public Map<UUID, GaleriaEnsaio> obterEmLote(Collection<UUID> agendamentoIds) {
        if (agendamentoIds == null || agendamentoIds.isEmpty()) {
            return Map.of();
        }
        var modo = modoPadrao();
        var galerias = new HashMap<UUID, GaleriaEnsaio>();
        galeriaRepository.findAllById(agendamentoIds)
            .forEach(galeria -> galerias.put(galeria.getAgendamentoId(), galeria));
        agendamentoIds.forEach(agendamentoId -> galerias.computeIfAbsent(
            agendamentoId,
            chave -> GaleriaEnsaio.builder()
                .agendamentoId(chave)
                .modo(modo)
                .fase(faseInicial(modo))
                .build()));
        return galerias;
    }

    /**
     * Registra a primeira publicacao de fotos da galeria. Publicacoes
     * posteriores nao alteram a data original, usada para medir o tempo
     * ensaio -> publicacao.
     */
    public void registrarPublicacao(UUID agendamentoId, LocalDateTime publicadoEm) {
        var galeria = obterOuCriar(agendamentoId);
        var anterior = galeria.getPublicadaEm();
        galeria.registrarPublicacao(publicadoEm);
        if (galeria.getPublicadaEm() != null && !galeria.getPublicadaEm().equals(anterior)) {
            galeriaRepository.save(galeria);
            log.atInfo()
                .addKeyValue("event_action", "galeria.fotos_publicadas")
                .addKeyValue("agendamento_id", agendamentoId)
                .addKeyValue("publicada_em", galeria.getPublicadaEm().toString())
                .log("Primeira publicação de fotos registrada na galeria");
        }
    }

    public GaleriaEnsaio obterParaAtualizacao(UUID agendamentoId) {
        obterOuCriar(agendamentoId);
        return galeriaRepository.findByIdForUpdate(agendamentoId)
            .orElseThrow(() -> new IllegalStateException(
                "Galeria não encontrada após criação: " + agendamentoId));
    }

    public boolean podeAlterarModo(UUID agendamentoId) {
        if (selecaoRepository.existsByAgendamentoIdAndStatusIn(agendamentoId, STATUS_ATIVOS)) {
            return false;
        }
        if (selecaoRepository.existsByAgendamentoIdAndStatusIn(
                agendamentoId, List.of(StatusSelecao.PUBLICADA))) {
            return false;
        }
        return !temVendaOuDownload(agendamentoId) && !temEdicaoRealizada(agendamentoId);
    }

    public record EstadoArquivamento(boolean arquivada, LocalDateTime arquivadaEm, long bytesLiberados) {}

    /**
     * Estado de arquivamento derivado das fotos: a galeria está arquivada
     * quando tem fotos e todas já tiveram os arquivos expurgados.
     */
    @Transactional(readOnly = true)
    public EstadoArquivamento estadoArquivamento(UUID agendamentoId) {
        var total = fotoRepository.countByAgendamentoId(agendamentoId);
        var arquivadas = fotoRepository.countByAgendamentoIdAndArquivosRemovidosTrue(agendamentoId);
        if (total == 0 || arquivadas < total) {
            return new EstadoArquivamento(false, null, 0);
        }
        return new EstadoArquivamento(
            true,
            fotoRepository.buscarDataArquivamento(agendamentoId),
            fotoRepository.somarBytesLiberados(agendamentoId));
    }

    public GaleriaEnsaio alterarModo(UUID agendamentoId, ModoGaleria novoModo) {
        if (!novoModo.disponivelParaNovasGalerias()) {
            throw new UnprocessableException(ErrorCode.MODO_GALERIA_INVALIDO,
                "O fluxo direto da galeria foi desativado. Use o fluxo com seleção "
                    + "e edição ou o fluxo sem edição.");
        }
        var galeria = obterParaAtualizacao(agendamentoId);
        if (galeria.getModo() == novoModo) {
            return galeria;
        }
        if (selecaoRepository.existsByAgendamentoIdAndStatusIn(agendamentoId, STATUS_ATIVOS)) {
            throw new GaleriaEmEdicaoException("alterar o modo enquanto há seleção em andamento");
        }
        if (selecaoRepository.existsByAgendamentoIdAndStatusIn(
                agendamentoId, List.of(StatusSelecao.PUBLICADA))) {
            throw new GaleriaEmEdicaoException("alterar o modo após a publicação da seleção");
        }
        if (temVendaOuDownload(agendamentoId)) {
            throw new GaleriaEmEdicaoException("alterar o modo após venda ou download");
        }
        if (temEdicaoRealizada(agendamentoId)) {
            throw new GaleriaEmEdicaoException("alterar o modo após o envio de versões editadas");
        }
        galeria.setModo(novoModo);
        galeria.setFase(faseInicial(novoModo));
        galeria.setSelecaoAtualId(null);
        log.atInfo()
            .addKeyValue("event_action", "galeria.modo_alterado")
            .addKeyValue("agendamento_id", agendamentoId)
            .addKeyValue("modo", novoModo)
            .log("Modo da galeria alterado");
        return galeriaRepository.save(galeria);
    }

    /**
     * Define a qualidade das prévias públicas da galeria.
     *
     * <p>A aplicação nas fotos existentes é feita pelo {@code FotoService};
     * aqui apenas validamos e persistimos a escolha do staff.
     */
    public GaleriaEnsaio alterarQualidadePreview(UUID agendamentoId, QualidadePreview qualidade) {
        if (qualidade == null) {
            throw new UnprocessableException(ErrorCode.QUALIDADE_PREVIEW_INVALIDA,
                "Informe a qualidade da prévia.");
        }
        if (estadoArquivamento(agendamentoId).arquivada()) {
            throw new GaleriaArquivadaException();
        }
        var galeria = obterParaAtualizacao(agendamentoId);
        galeria.setQualidadePreview(qualidade);
        log.atInfo()
            .addKeyValue("event_action", "galeria.qualidade_preview_alterada")
            .addKeyValue("agendamento_id", agendamentoId)
            .addKeyValue("qualidade_preview", qualidade.name())
            .log("Qualidade da prévia da galeria alterada");
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
        if (galeria.getModo().usaSelecaoSnapshot()
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

    /**
     * Define ou remove o texto customizado exibido no hero da galeria.
     *
     * <p>Espaços em excesso são normalizados e texto em branco volta a
     * {@code null}, fazendo o hero exibir o nome do cliente novamente.
     */
    public GaleriaEnsaio definirTituloHero(UUID agendamentoId, String tituloHero) {
        var galeria = obterParaAtualizacao(agendamentoId);
        var texto = tituloHero == null ? null : tituloHero.replaceAll("\\s+", " ").strip();
        if (texto != null && texto.isEmpty()) {
            texto = null;
        }
        galeria.setTituloHero(texto);
        log.atInfo()
            .addKeyValue("event_action", texto == null
                ? "galeria.titulo_hero_removido"
                : "galeria.titulo_hero_definido")
            .addKeyValue("agendamento_id", agendamentoId)
            .log("Texto do hero da galeria atualizado");
        return galeriaRepository.save(galeria);
    }

    /**
     * Reabertura permitida enquanto nenhuma foto foi baixada ou vendida.
     * Usada pela reabertura de uma seleção SEM_EDICAO já aprovada.
     */
    public boolean podeReabrirSelecao(UUID agendamentoId) {
        return !temVendaOuDownload(agendamentoId);
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

    /**
     * Existe trabalho de edicao ja feito para este ensaio. Trocar para o modo
     * sem edicao descartaria esse trabalho e mudaria o entregavel do cliente.
     */
    private boolean temEdicaoRealizada(UUID agendamentoId) {
        var fotos = fotoRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId);
        return fotos.stream().anyMatch(f ->
            f.getEditadaPath() != null || f.getVersaoAtual() == VersaoFoto.EDITADA);
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
        return modo.usaSelecaoSnapshot()
            ? FaseGaleria.SELECAO
            : FaseGaleria.COMERCIALIZACAO;
    }

    private ModoGaleria modoPadrao() {
        var valor = configuracaoService.getValor(ConfigKey.GALERIA_MODO_PADRAO);
        if (valor != null && !valor.isBlank()) {
            try {
                var configurado = ModoGaleria.valueOf(valor.trim().toUpperCase());
                if (configurado.disponivelParaNovasGalerias()) {
                    return configurado;
                }
            } catch (IllegalArgumentException ignored) {
                // Valor invalido: cai no default abaixo.
            }
            log.atWarn()
                .addKeyValue("event_action", "galeria.modo_padrao_ignorado")
                .addKeyValue("valor", valor)
                .log("Modo padrão de galeria inválido ou desativado; usando COM_EDICAO");
        }
        return ModoGaleria.COM_EDICAO;
    }
}
