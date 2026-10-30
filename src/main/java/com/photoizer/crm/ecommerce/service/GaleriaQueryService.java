package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.ecommerce.exception.GaleriaArquivadaException;
import com.photoizer.crm.ecommerce.exception.GaleriaNaoEncontradaException;
import com.photoizer.crm.ecommerce.exception.TokenExpiradoException;
import com.photoizer.crm.ecommerce.exception.FotoNaoEncontradaException;
import com.photoizer.crm.ecommerce.model.StatusSelecao;
import com.photoizer.crm.ecommerce.repository.SelecaoEnsaioItemRepository;
import com.photoizer.crm.ecommerce.repository.SelecaoEnsaioRepository;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.VersaoFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PATTERN: Facade Pattern
 * Fachada para consultas read-only da galeria. Centraliza as queries de
 * agendamento por token, valor unitário de foto extra e listagem de fotos
 * publicadas. Motivo: eliminar a duplicação de buscarAgendamentoPorToken()
 * que existia em EcommerceService e ComentarioService, e isolar a lógica
 * de resolução de token/validação de expiração em um único lugar.
 */
@Service
@Transactional(readOnly = true)
public class GaleriaQueryService {

    private final AgendamentoRepository agendamentoRepository;
    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final ConfiguracaoService configuracaoService;
    private final GaleriaEnsaioService galeriaEnsaioService;
    private final SelecaoEnsaioRepository selecaoRepository;
    private final SelecaoEnsaioItemRepository selecaoItemRepository;

    public GaleriaQueryService(AgendamentoRepository agendamentoRepository,
                               FotoEnsaioRepository fotoEnsaioRepository,
                               ConfiguracaoService configuracaoService,
                               GaleriaEnsaioService galeriaEnsaioService,
                               SelecaoEnsaioRepository selecaoRepository,
                               SelecaoEnsaioItemRepository selecaoItemRepository) {
        this.agendamentoRepository = agendamentoRepository;
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.configuracaoService = configuracaoService;
        this.galeriaEnsaioService = galeriaEnsaioService;
        this.selecaoRepository = selecaoRepository;
        this.selecaoItemRepository = selecaoItemRepository;
    }

    public Agendamento buscarAgendamentoPorToken(UUID token) {
        var agendamento = agendamentoRepository.findByTokenGaleria(token)
            .orElseThrow(() -> new GaleriaNaoEncontradaException(token));
        if (agendamento.getTokenExpiracao() != null && agendamento.getTokenExpiracao().isBefore(LocalDateTime.now())) {
            throw new TokenExpiradoException("O link da galeria expirou. Solicite um novo link ao fotógrafo.");
        }
        if (isGaleriaArquivada(agendamento.getId())) {
            throw new GaleriaArquivadaException();
        }
        return agendamento;
    }

    /**
     * Galeria arquivada = tem fotos e todas já tiveram os arquivos expurgados.
     * Uma galeria sem fotos não é considerada arquivada.
     */
    public boolean isGaleriaArquivada(UUID agendamentoId) {
        var total = fotoEnsaioRepository.countByAgendamentoId(agendamentoId);
        if (total == 0) {
            return false;
        }
        return fotoEnsaioRepository.countByAgendamentoIdAndArquivosRemovidosTrue(agendamentoId) >= total;
    }

    public BigDecimal getValorUnitarioFotoExtra() {
        return configuracaoService.getValorDecimal(ConfigKey.VALOR_FOTO_EXTRA);
    }

    public BigDecimal getValorUnitarioFotoExtra(UUID agendamentoId) {
        return agendamentoRepository.findById(agendamentoId)
            .map(agendamento -> agendamento.getPacote().getPrecoFotoExtra())
            .filter(preco -> preco != null && preco.signum() > 0)
            .orElseGet(this::getValorUnitarioFotoExtra);
    }

    /**
     * Preco do extra congelado no snapshot da selecao. Considera a selecao
     * ativa (ENVIADA/EM_EDICAO/CONCLUIDA) e a ultima publicada, para que o
     * carrinho use o mesmo valor durante a edicao e depois da publicacao.
     * Se a foto nao fizer parte de nenhuma selecao, usa a configuracao atual.
     */
    public BigDecimal getValorUnitarioExtra(UUID agendamentoId, UUID fotoId) {
        var selecao = buscarSelecaoComPrecoCongelado(agendamentoId);
        return selecao
            .flatMap(s -> selecaoItemRepository.findBySelecaoIdAndFotoId(s.getId(), fotoId))
            .map(com.photoizer.crm.ecommerce.model.SelecaoEnsaioItem::getValorUnitario)
            .filter(java.util.Objects::nonNull)
            .orElseGet(() -> getValorUnitarioFotoExtra(agendamentoId));
    }

    private java.util.Optional<com.photoizer.crm.ecommerce.model.SelecaoEnsaio> buscarSelecaoComPrecoCongelado(
            UUID agendamentoId) {
        return selecaoRepository.findFirstByAgendamentoIdAndStatusInOrderByNumeroDesc(
            agendamentoId,
            List.of(
                StatusSelecao.ENVIADA,
                StatusSelecao.EM_EDICAO,
                StatusSelecao.CONCLUIDA,
                StatusSelecao.PUBLICADA));
    }

    public List<FotoEnsaio> listarFotosPublicadas(UUID token) {
        var agendamento = buscarAgendamentoPorToken(token);
        var fotos = fotoEnsaioRepository.findPublicadasVisiveisByAgendamentoId(agendamento.getId());
        var galeria = galeriaEnsaioService.obter(agendamento.getId());

        if (!galeria.getModo().usaSelecaoSnapshot()) {
            return fotos;
        }

        if (!galeria.getFase().permiteComercializar()) {
            return fotos;
        }

        // Em comercializacao a galeria mostra as fotos da selecao publicada
        // (editadas) e tambem as fotos restantes, que ficam disponiveis para
        // compra com edicao sob demanda. O frontend distingue as duas pela
        // versao atual: EDITADA = pronta; ORIGINAL = sera editada apos a compra.
        return fotos;
    }

    public List<FotoEnsaio> listarFotosPorAgendamento(UUID agendamentoId) {
        return fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId);
    }

    public Agendamento buscarAgendamentoPorId(UUID agendamentoId) {
        return agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new GaleriaNaoEncontradaException("Agendamento nao encontrado: " + agendamentoId));
    }

    public FotoEnsaio buscarFotoPorId(UUID fotoId) {
        return fotoEnsaioRepository.findById(fotoId)
            .orElseThrow(() -> new FotoNaoEncontradaException(fotoId));
    }

    public boolean isDownloadPermitido(FotoEnsaio foto) {
        if (!foto.isVisivel()) {
            return false;
        }
        if (foto.getStatus() != com.photoizer.crm.foto.model.StatusFoto.PUBLICADA
            && foto.getStatus() != com.photoizer.crm.foto.model.StatusFoto.PAGA) {
            return false;
        }
        var galeria = galeriaEnsaioService.obter(foto.getAgendamentoId());
        if (!galeria.permiteComercializar()) {
            return false;
        }
        if (galeria.getModo().requerEdicao()) {
            if (foto.getVersaoAtual() != VersaoFoto.EDITADA) {
                return false;
            }
            // Fotos compradas continuam disponíveis em "Minhas compras" mesmo
            // que uma nova seleção tenha sido publicada depois.
            if (foto.getStatus() != com.photoizer.crm.foto.model.StatusFoto.PAGA
                && !fotoEmSelecaoPublicada(foto)) {
                return false;
            }
        }
        // Nos modos ENTREGA_DIRETA e DIRETO o entregável é o arquivo já publicado.
        return foto.isSelecionadaPacote()
            || foto.getStatus() == com.photoizer.crm.foto.model.StatusFoto.PAGA;
    }

    /**
     * Confere se a foto pode entrar no carrinho agora.
     *
     * <p>No modo DIRETO basta estar publicada, visível e sem compra. No modo
     * COM_EDICAO o carrinho também funciona durante a seleção e a edição: os
     * extras escolhidos ali são anexados ao snapshot da seleção ativa para
     * serem editados junto com o pacote. Na comercialização vale a regra de
     * {@link #isFotoComercializavel(FotoEnsaio)}.
     */
    public boolean isFotoDisponivelParaCarrinho(FotoEnsaio foto) {
        var galeria = galeriaEnsaioService.obter(foto.getAgendamentoId());
        if (!galeria.getModo().usaSelecaoSnapshot()) {
            return true;
        }
        var fase = galeria.getFase();
        if (fase == com.photoizer.crm.ecommerce.model.FaseGaleria.SELECAO
            || fase == com.photoizer.crm.ecommerce.model.FaseGaleria.SELECAO_ENVIADA
            || fase == com.photoizer.crm.ecommerce.model.FaseGaleria.EM_EDICAO) {
            // Durante a seleção/edição o cliente marca extras que serão
            // editados junto com o pacote (ou aprovados junto, no fluxo sem
            // edição); ainda não há compra.
            return true;
        }
        return isFotoComercializavel(foto);
    }

    /**
     * Confere se a foto pode ser comprada agora.
     *
     * <p>No modo DIRETO basta estar publicada, visível e sem compra. No modo
     * COM_EDICAO/COMERCIALIZACAO, as fotos da seleção publicada precisam estar
     * na versão editada. As fotos restantes (fora da seleção) também podem ser
     * compradas: elas entram no fluxo de edição sob demanda antes do download.
     */
    public boolean isFotoComercializavel(FotoEnsaio foto) {
        if (!foto.isVisivel()
            || foto.getStatus() != com.photoizer.crm.foto.model.StatusFoto.PUBLICADA
            || foto.getCompraExtraId() != null) {
            return false;
        }
        var galeria = galeriaEnsaioService.obter(foto.getAgendamentoId());
        if (!galeria.getModo().usaSelecaoSnapshot()) {
            return true;
        }
        if (!galeria.getFase().permiteComercializar()) {
            return false;
        }
        if (galeria.getModo().requerEdicao() && fotoEmSelecaoPublicada(foto)) {
            return foto.getVersaoAtual() == VersaoFoto.EDITADA;
        }
        // ENTREGA_DIRETA: a foto da seleção publicada é entregue como foi publicada.
        // COM_EDICAO: foto restante será editada pelo estúdio após a compra.
        return true;
    }

    private boolean fotoEmSelecaoPublicada(FotoEnsaio foto) {
        var selecao = selecaoRepository
            .findFirstByAgendamentoIdAndStatusInOrderByNumeroDesc(
                foto.getAgendamentoId(), List.of(StatusSelecao.PUBLICADA));
        return selecao
            .map(s -> selecaoItemRepository.findBySelecaoIdAndFotoId(s.getId(), foto.getId()).isPresent())
            .orElse(false);
    }

    /**
     * Preço congelado de cada foto extra, por foto. Fotos do pacote recebem o
     * preço padrão apenas para preencher o mapa; o frontend só usa os extras.
     */
    public Map<UUID, BigDecimal> getValoresExtras(UUID agendamentoId, List<UUID> fotoIds) {
        var padrao = getValorUnitarioFotoExtra(agendamentoId);
        var congelados = new HashMap<UUID, BigDecimal>();
        buscarSelecaoComPrecoCongelado(agendamentoId)
            .ifPresent(selecao -> selecaoItemRepository
                .findBySelecaoIdOrderByNumeroGaleriaAsc(selecao.getId())
                .stream()
                .filter(item -> item.getValorUnitario() != null)
                .forEach(item -> congelados.put(item.getFotoId(), item.getValorUnitario())));

        var valores = new HashMap<UUID, BigDecimal>();
        fotoIds.forEach(fotoId -> valores.put(fotoId, congelados.getOrDefault(fotoId, padrao)));
        return valores;
    }
}
