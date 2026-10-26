package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.ecommerce.api.SelecaoEnsaioItemResponse;
import com.photoizer.crm.ecommerce.api.SelecaoEnsaioResponse;
import com.photoizer.crm.ecommerce.api.SelecaoPendenteResponse;
import com.photoizer.crm.ecommerce.event.EdicaoPublicadaEvent;
import com.photoizer.crm.ecommerce.event.SelecaoCanceladaEvent;
import com.photoizer.crm.ecommerce.event.SelecaoEnviadaEvent;
import com.photoizer.crm.ecommerce.event.SelecaoExtraAdicionadaEvent;
import com.photoizer.crm.ecommerce.event.SelecaoExtraRemovidaEvent;
import com.photoizer.crm.ecommerce.exception.FotoIndisponivelException;
import com.photoizer.crm.ecommerce.exception.FotoNaoEncontradaException;
import com.photoizer.crm.ecommerce.exception.FotosEditadasIncompletasException;
import com.photoizer.crm.ecommerce.exception.SelecaoJaEnviadaException;
import com.photoizer.crm.ecommerce.exception.SelecaoNaoEditavelException;
import com.photoizer.crm.ecommerce.exception.SelecaoNaoEncontradaException;
import com.photoizer.crm.ecommerce.model.ModoGaleria;
import com.photoizer.crm.ecommerce.model.SelecaoEnsaio;
import com.photoizer.crm.ecommerce.model.SelecaoEnsaioItem;
import com.photoizer.crm.ecommerce.model.StatusSelecao;
import com.photoizer.crm.ecommerce.model.TipoItemSelecao;
import com.photoizer.crm.ecommerce.repository.SelecaoEnsaioItemRepository;
import com.photoizer.crm.ecommerce.repository.SelecaoEnsaioRepository;
import com.photoizer.crm.foto.api.FotoMapper;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orquestra o ciclo da selecao enviada pelo cliente e da edicao feita pelo
 * staff.
 *
 * <p>O snapshot e imutavel: alteracoes exigem reabertura. O modulo ecommerce
 * nao escreve em {@link FotoEnsaio}; a marcacao de {@code emSelecaoEdicao} e a
 * troca de versao publica sao feitas por listeners de eventos no modulo foto.
 */
@Service
@Transactional
public class SelecaoEnsaioService {

    private static final Logger log = LoggerFactory.getLogger(SelecaoEnsaioService.class);

    private static final List<StatusSelecao> STATUS_ATIVOS = List.of(
        StatusSelecao.ENVIADA, StatusSelecao.EM_EDICAO, StatusSelecao.CONCLUIDA);

    private final SelecaoEnsaioRepository selecaoRepository;
    private final SelecaoEnsaioItemRepository itemRepository;
    private final GaleriaEnsaioService galeriaService;
    private final GaleriaQueryService galeriaQueryService;
    private final CarrinhoService carrinhoService;
    private final SelecaoFotosService selecaoFotosService;
    private final SelecaoFotoValidator selecaoFotoValidator;
    private final FotoEnsaioRepository fotoRepository;
    private final FotoMapper fotoMapper;
    private final ApplicationEventPublisher eventPublisher;

    public SelecaoEnsaioService(SelecaoEnsaioRepository selecaoRepository,
                                SelecaoEnsaioItemRepository itemRepository,
                                GaleriaEnsaioService galeriaService,
                                GaleriaQueryService galeriaQueryService,
                                CarrinhoService carrinhoService,
                                SelecaoFotosService selecaoFotosService,
                                SelecaoFotoValidator selecaoFotoValidator,
                                FotoEnsaioRepository fotoRepository,
                                FotoMapper fotoMapper,
                                ApplicationEventPublisher eventPublisher) {
        this.selecaoRepository = selecaoRepository;
        this.itemRepository = itemRepository;
        this.galeriaService = galeriaService;
        this.galeriaQueryService = galeriaQueryService;
        this.carrinhoService = carrinhoService;
        this.selecaoFotosService = selecaoFotosService;
        this.selecaoFotoValidator = selecaoFotoValidator;
        this.fotoRepository = fotoRepository;
        this.fotoMapper = fotoMapper;
        this.eventPublisher = eventPublisher;
    }

    public SelecaoEnsaioResponse enviar(UUID token, UUID sessionId,
                                        List<UUID> fotoIdsPacote, String observacao) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        var galeria = galeriaService.obterParaAtualizacao(agendamento.getId());

        if (galeria.getModo() != ModoGaleria.COM_EDICAO) {
            throw new SelecaoNaoEditavelException(
                "Esta galeria não está habilitada para envio de seleção para edição");
        }
        galeriaService.validarSelecaoPermitida(galeria);
        if (selecaoRepository.existsByAgendamentoIdAndStatusIn(agendamento.getId(), STATUS_ATIVOS)) {
            throw new SelecaoJaEnviadaException();
        }

        var pacoteIds = new LinkedHashSet<UUID>();
        if (fotoIdsPacote != null) {
            fotoIdsPacote.stream().filter(id -> id != null).forEach(pacoteIds::add);
        }
        if (pacoteIds.isEmpty()) {
            throw new SelecaoNaoEditavelException("Selecione ao menos uma foto do pacote");
        }

        var fotosAtuais = fotoRepository.findByAgendamentoIdOrderByOrdemAsc(agendamento.getId());
        validarFotosDoPacote(fotosAtuais, pacoteIds, agendamento);

        // Uma foto que entrou no pacote não pode permanecer no carrinho: ela
        // seria cobrada de novo (e o checkout recusaria a compra inteira).
        if (sessionId != null) {
            carrinhoService.listarCarrinho(token, sessionId).stream()
                .map(com.photoizer.crm.ecommerce.model.ItemCarrinho::getFotoId)
                .filter(pacoteIds::contains)
                .distinct()
                .forEach(fotoId -> carrinhoService.removerDoCarrinho(token, sessionId, fotoId));
        }

        // Sincroniza a selecao "rascunho" com o que o cliente enviou, de forma
        // atomica. A remocao e feita antes da adicao para respeitar o limite.
        var selecionadasAtuais = fotosAtuais.stream()
            .filter(FotoEnsaio::isSelecionadaPacote)
            .map(FotoEnsaio::getId)
            .toList();
        var remover = selecionadasAtuais.stream()
            .filter(id -> !pacoteIds.contains(id))
            .toList();
        var adicionar = pacoteIds.stream()
            .filter(id -> !selecionadasAtuais.contains(id))
            .toList();
        if (!remover.isEmpty()) {
            selecaoFotosService.selecionarFotos(token, remover, false);
        }
        if (!adicionar.isEmpty()) {
            selecaoFotosService.selecionarFotos(token, adicionar, true);
        }

        var fotosPorId = fotoRepository.findAllById(pacoteIds).stream()
            .collect(Collectors.toMap(FotoEnsaio::getId, Function.identity()));
        var extras = carregarExtras(token, sessionId, pacoteIds, agendamento);

        var numero = selecaoRepository.findFirstByAgendamentoIdOrderByNumeroDesc(agendamento.getId())
            .map(ultima -> ultima.getNumero() + 1)
            .orElse(1);

        var selecao = SelecaoEnsaio.builder()
            .agendamentoId(agendamento.getId())
            .numero(numero)
            .status(StatusSelecao.ENVIADA)
            .observacaoCliente(observacao)
            .enviadaEm(LocalDateTime.now())
            .build();
        var selecaoSalva = selecaoRepository.save(selecao);

        var itens = new ArrayList<SelecaoEnsaioItem>();
        fotosPorId.values().stream()
            .sorted(Comparator.comparing(FotoEnsaio::getOrdem))
            .forEach(foto -> itens.add(novoItem(selecaoSalva.getId(), foto, TipoItemSelecao.PACOTE, null)));
        extras.stream()
            .sorted(Comparator.comparing(FotoEnsaio::getOrdem))
            .forEach(foto -> itens.add(novoItem(selecaoSalva.getId(), foto, TipoItemSelecao.EXTRA,
                galeriaQueryService.getValorUnitarioFotoExtra(agendamento.getId()))));
        itemRepository.saveAll(itens);

        galeria.iniciarSelecao(selecaoSalva.getId());
        eventPublisher.publishEvent(new SelecaoEnviadaEvent(
            agendamento.getId(),
            selecaoSalva.getId(),
            itens.stream().map(SelecaoEnsaioItem::getFotoId).toList(),
            (int) itens.stream().filter(i -> i.getTipo() == TipoItemSelecao.PACOTE).count(),
            (int) itens.stream().filter(i -> i.getTipo() == TipoItemSelecao.EXTRA).count(),
            agendamento.nomeCliente(),
            agendamento.getEditor() != null ? agendamento.getEditor().getId() : null,
            agendamento.getFotografo() != null ? agendamento.getFotografo().getId() : null
        ));

        log.atInfo()
            .addKeyValue("event_action", "selecao.enviada")
            .addKeyValue("agendamento_id", agendamento.getId())
            .addKeyValue("selecao_id", selecaoSalva.getId())
            .addKeyValue("quantidade_pacote", fotosPorId.size())
            .addKeyValue("quantidade_extras", extras.size())
            .log("Seleção enviada para edição");

        return paraResponse(selecaoSalva, itens);
    }

    /**
     * Anexa uma foto extra ao snapshot da selecao ativa. Chamado quando o
     * cliente adiciona um extra ao carrinho enquanto a selecao ja foi enviada
     * e o estudio ainda esta editando. Sem isso o extra ficaria no carrinho
     * sem entrar no trabalho de edicao.
     */
    public void incluirExtra(UUID agendamentoId, UUID fotoId) {
        var selecaoOpt = selecaoRepository.findFirstByAgendamentoIdAndStatusInOrderByNumeroDesc(
            agendamentoId, STATUS_ATIVOS);
        if (selecaoOpt.isEmpty()) {
            return;
        }
        var selecao = selecaoRepository.findByIdForUpdate(selecaoOpt.get().getId())
            .orElse(selecaoOpt.get());
        if (itemRepository.findBySelecaoIdAndFotoId(selecao.getId(), fotoId).isPresent()) {
            return;
        }

        var agendamento = galeriaQueryService.buscarAgendamentoPorId(agendamentoId);
        var foto = fotoRepository.findById(fotoId)
            .orElseThrow(() -> new FotoNaoEncontradaException(fotoId));
        if (foto.isSelecionadaPacote()) {
            throw new FotoIndisponivelException(
                "Foto já incluída no pacote não pode ser adicionada como extra");
        }
        selecaoFotoValidator.validar(foto, agendamento);

        var item = novoItem(
            selecao.getId(),
            foto,
            TipoItemSelecao.EXTRA,
            galeriaQueryService.getValorUnitarioFotoExtra(agendamentoId));
        itemRepository.save(item);

        if (selecao.getStatus() == StatusSelecao.CONCLUIDA) {
            selecao.reabrirEdicao();
            selecaoRepository.save(selecao);
        }

        eventPublisher.publishEvent(new SelecaoExtraAdicionadaEvent(
            agendamentoId, selecao.getId(), fotoId));

        log.atInfo()
            .addKeyValue("event_action", "selecao.extra_adicionado")
            .addKeyValue("agendamento_id", agendamentoId)
            .addKeyValue("selecao_id", selecao.getId())
            .addKeyValue("foto_id", fotoId)
            .addKeyValue("valor_unitario", item.getValorUnitario())
            .log("Extra anexado à seleção em edição");
    }

    /**
     * Remove um extra do snapshot da selecao ativa. So e permitido enquanto a
     * foto ainda nao foi editada; depois disso o estudio precisa ser acionado.
     */
    public void removerExtra(UUID agendamentoId, UUID fotoId) {
        var selecaoOpt = selecaoRepository.findFirstByAgendamentoIdAndStatusInOrderByNumeroDesc(
            agendamentoId, STATUS_ATIVOS);
        if (selecaoOpt.isEmpty()) {
            return;
        }
        var selecao = selecaoRepository.findByIdForUpdate(selecaoOpt.get().getId())
            .orElse(selecaoOpt.get());
        var item = itemRepository.findBySelecaoIdAndFotoId(selecao.getId(), fotoId).orElse(null);
        if (item == null || item.getTipo() != TipoItemSelecao.EXTRA) {
            return;
        }
        if (item.isEditada()) {
            throw new SelecaoNaoEditavelException(
                "A foto extra já foi editada e não pode ser removida do carrinho. "
                    + "Fale com o estúdio para ajustar a seleção.");
        }

        itemRepository.delete(item);
        eventPublisher.publishEvent(new SelecaoExtraRemovidaEvent(
            agendamentoId, selecao.getId(), fotoId));

        if (selecao.getStatus() == StatusSelecao.EM_EDICAO) {
            var total = itemRepository.countBySelecaoId(selecao.getId());
            var editadas = itemRepository.countBySelecaoIdAndEditadaTrue(selecao.getId());
            if (total > 0 && total == editadas) {
                selecao.concluir();
                selecaoRepository.save(selecao);
            }
        }

        log.atInfo()
            .addKeyValue("event_action", "selecao.extra_removido")
            .addKeyValue("agendamento_id", agendamentoId)
            .addKeyValue("selecao_id", selecao.getId())
            .addKeyValue("foto_id", fotoId)
            .log("Extra removido da seleção em edição");
    }

    public SelecaoEnsaioResponse detalhar(UUID agendamentoId, UUID selecaoId) {
        var selecao = buscarSelecao(agendamentoId, selecaoId);
        return paraResponse(selecao, itemRepository.findBySelecaoIdOrderByNumeroGaleriaAsc(selecaoId));
    }

    public java.util.Optional<SelecaoEnsaioResponse> obterAtiva(UUID token) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        return selecaoRepository.findFirstByAgendamentoIdAndStatusInOrderByNumeroDesc(
                agendamento.getId(), STATUS_ATIVOS)
            .map(selecao -> paraResponse(selecao,
                itemRepository.findBySelecaoIdOrderByNumeroGaleriaAsc(selecao.getId())));
    }

    public List<SelecaoEnsaioResponse> listarDoAgendamento(UUID agendamentoId) {
        return selecaoRepository.findByAgendamentoIdOrderByNumeroDesc(agendamentoId).stream()
            .map(selecao -> paraResponse(selecao,
                itemRepository.findBySelecaoIdOrderByNumeroGaleriaAsc(selecao.getId())))
            .toList();
    }

    public List<SelecaoPendenteResponse> listarPendentes() {
        return selecaoRepository.findByStatusInOrderByEnviadaEmAsc(STATUS_ATIVOS).stream()
            .map(selecao -> {
                var agendamento = galeriaQueryService.buscarAgendamentoPorId(selecao.getAgendamentoId());
                var itens = itemRepository.findBySelecaoIdOrderByNumeroGaleriaAsc(selecao.getId());
                return new SelecaoPendenteResponse(
                    selecao.getAgendamentoId(),
                    selecao.getId(),
                    selecao.getNumero(),
                    selecao.getStatus().name(),
                    agendamento.nomeCliente(),
                    agendamento.getPacote().getNome(),
                    itens.stream().filter(i -> i.getTipo() == TipoItemSelecao.PACOTE).count(),
                    itens.stream().filter(i -> i.getTipo() == TipoItemSelecao.EXTRA).count(),
                    selecao.getEnviadaEm(),
                    Math.max(0, ChronoUnit.DAYS.between(
                        selecao.getEnviadaEm().toLocalDate(), LocalDate.now()))
                );
            })
            .toList();
    }

    public SelecaoEnsaioResponse iniciar(UUID agendamentoId, UUID selecaoId) {
        var selecao = buscarSelecao(agendamentoId, selecaoId);
        if (selecao.getStatus() == StatusSelecao.ENVIADA) {
            selecao.iniciarEdicao();
            var galeria = galeriaService.obterParaAtualizacao(agendamentoId);
            galeria.iniciarEdicao();
            selecaoRepository.save(selecao);
        }
        return paraResponse(selecao, itemRepository.findBySelecaoIdOrderByNumeroGaleriaAsc(selecaoId));
    }

    public void registrarDownloadOriginais(UUID agendamentoId, UUID selecaoId) {
        var selecao = buscarSelecao(agendamentoId, selecaoId);
        if (selecao.getStatus() == StatusSelecao.ENVIADA) {
            selecao.iniciarEdicao();
        }
        selecao.registrarDownloadOriginais();
        selecaoRepository.save(selecao);
        if (selecao.getStatus() == StatusSelecao.EM_EDICAO) {
            var galeria = galeriaService.obterParaAtualizacao(agendamentoId);
            galeria.iniciarEdicao();
        }
    }

    public SelecaoEnsaioResponse reabrir(UUID agendamentoId, UUID selecaoId) {
        var selecao = buscarSelecao(agendamentoId, selecaoId);
        if (selecao.getStatus() == StatusSelecao.PUBLICADA) {
            throw new SelecaoNaoEditavelException(
                "Seleção já publicada não pode ser reaberta. Crie uma nova seleção.");
        }
        var itens = itemRepository.findBySelecaoIdOrderByNumeroGaleriaAsc(selecaoId);
        selecao.cancelar();
        selecaoRepository.save(selecao);

        var galeria = galeriaService.obterParaAtualizacao(agendamentoId);
        galeria.reabrirSelecao();
        eventPublisher.publishEvent(new SelecaoCanceladaEvent(
            agendamentoId, selecaoId, itens.stream().map(SelecaoEnsaioItem::getFotoId).toList()));

        log.atInfo()
            .addKeyValue("event_action", "selecao.reaberta")
            .addKeyValue("agendamento_id", agendamentoId)
            .addKeyValue("selecao_id", selecaoId)
            .log("Seleção reaberta para o cliente");
        return paraResponse(selecao, itens);
    }

    public SelecaoEnsaioResponse publicar(UUID agendamentoId, UUID selecaoId) {
        // O lock serializa a publicação com a inclusão de extras pelo cliente.
        // Sem ele, um extra poderia entrar depois da checagem de "tudo editado".
        var selecao = buscarSelecaoParaAtualizacao(agendamentoId, selecaoId);
        var itens = itemRepository.findBySelecaoIdOrderByNumeroGaleriaAsc(selecaoId);
        var pendentes = itens.stream().filter(i -> !i.isEditada()).count();
        if (pendentes > 0) {
            throw new FotosEditadasIncompletasException(pendentes);
        }
        if (selecao.getStatus() == StatusSelecao.PUBLICADA) {
            return paraResponse(selecao, itens);
        }
        if (selecao.getStatus() == StatusSelecao.ENVIADA) {
            selecao.iniciarEdicao();
        }
        if (selecao.getStatus() == StatusSelecao.EM_EDICAO) {
            selecao.concluir();
        }
        selecao.publicar();
        selecaoRepository.save(selecao);

        var galeria = galeriaService.obterParaAtualizacao(agendamentoId);
        galeria.publicarEdicao();
        eventPublisher.publishEvent(new EdicaoPublicadaEvent(
            agendamentoId, selecaoId, itens.stream().map(SelecaoEnsaioItem::getFotoId).toList()));

        log.atInfo()
            .addKeyValue("event_action", "selecao.publicada")
            .addKeyValue("agendamento_id", agendamentoId)
            .addKeyValue("selecao_id", selecaoId)
            .addKeyValue("quantidade", itens.size())
            .log("Edição publicada para o cliente");
        return paraResponse(selecao, itens);
    }

    /**
     * Consumido pelo listener de {@code FotoEditadaEvent}: marca o item do
     * snapshot ativo e conclui a selecao quando todos estiverem editados.
     */
    public void registrarFotoEditada(UUID agendamentoId, UUID fotoId) {
        selecaoRepository.findFirstByAgendamentoIdAndStatusInOrderByNumeroDesc(
                agendamentoId, List.of(StatusSelecao.ENVIADA, StatusSelecao.EM_EDICAO, StatusSelecao.CONCLUIDA))
            .ifPresent(selecao -> itemRepository.findBySelecaoIdAndFotoId(selecao.getId(), fotoId)
                .ifPresent(item -> {
                    item.marcarEditada();
                    itemRepository.save(item);
                    var total = itemRepository.countBySelecaoId(selecao.getId());
                    var editadas = itemRepository.countBySelecaoIdAndEditadaTrue(selecao.getId());
                    if (total > 0 && total == editadas
                        && selecao.getStatus() == StatusSelecao.EM_EDICAO) {
                        selecao.concluir();
                        selecaoRepository.save(selecao);
                        log.atInfo()
                            .addKeyValue("event_action", "selecao.concluida")
                            .addKeyValue("selecao_id", selecao.getId())
                            .addKeyValue("quantidade", total)
                            .log("Todas as fotos da seleção foram editadas");
                    }
                }));
    }

    private SelecaoEnsaio buscarSelecao(UUID agendamentoId, UUID selecaoId) {
        var selecao = selecaoRepository.findById(selecaoId)
            .orElseThrow(() -> new SelecaoNaoEncontradaException(selecaoId));
        if (!selecao.getAgendamentoId().equals(agendamentoId)) {
            throw new SelecaoNaoEncontradaException(selecaoId);
        }
        return selecao;
    }

    private SelecaoEnsaio buscarSelecaoParaAtualizacao(UUID agendamentoId, UUID selecaoId) {
        var selecao = selecaoRepository.findByIdForUpdate(selecaoId)
            .orElseThrow(() -> new SelecaoNaoEncontradaException(selecaoId));
        if (!selecao.getAgendamentoId().equals(agendamentoId)) {
            throw new SelecaoNaoEncontradaException(selecaoId);
        }
        return selecao;
    }

    private void validarFotosDoPacote(List<FotoEnsaio> fotos, Set<UUID> pacoteIds, Agendamento agendamento) {
        if (pacoteIds.size() > agendamento.getPacote().getQuantidadeFotos()) {
            throw new com.photoizer.crm.ecommerce.exception.LimitePacoteExcedidoException(
                agendamento.getPacote().getQuantidadeFotos());
        }
        var porId = fotos.stream().collect(Collectors.toMap(FotoEnsaio::getId, Function.identity()));
        for (var id : pacoteIds) {
            var foto = porId.get(id);
            if (foto == null) {
                throw new com.photoizer.crm.ecommerce.exception.FotoNaoEncontradaException(id);
            }
            selecaoFotoValidator.validar(foto, agendamento);
        }
    }

    private List<FotoEnsaio> carregarExtras(UUID token, UUID sessionId, Set<UUID> pacoteIds,
                                            Agendamento agendamento) {
        if (sessionId == null) {
            return List.of();
        }
        var itens = carrinhoService.listarCarrinho(token, sessionId);
        var extrasIds = itens.stream()
            .map(com.photoizer.crm.ecommerce.model.ItemCarrinho::getFotoId)
            .filter(id -> !pacoteIds.contains(id))
            .distinct()
            .toList();
        if (extrasIds.isEmpty()) {
            return List.of();
        }
        var fotos = fotoRepository.findAllById(extrasIds);
        for (var foto : fotos) {
            selecaoFotoValidator.validar(foto, agendamento);
        }
        return fotos;
    }

    private SelecaoEnsaioItem novoItem(UUID selecaoId, FotoEnsaio foto, TipoItemSelecao tipo,
                                       BigDecimal valorUnitario) {
        return SelecaoEnsaioItem.builder()
            .selecaoId(selecaoId)
            .fotoId(foto.getId())
            .numeroGaleria(foto.getNumeroGaleria())
            .tipo(tipo)
            .ordem(foto.getOrdem())
            .nomeArquivo(foto.getFileName())
            .valorUnitario(valorUnitario)
            .editada(foto.getVersaoAtual() == com.photoizer.crm.foto.model.VersaoFoto.EDITADA)
            .build();
    }

    private SelecaoEnsaioResponse paraResponse(SelecaoEnsaio selecao, List<SelecaoEnsaioItem> itens) {
        var fotos = fotoRepository.findAllById(itens.stream().map(SelecaoEnsaioItem::getFotoId).toList())
            .stream().collect(Collectors.toMap(FotoEnsaio::getId, Function.identity()));
        var itensResponse = itens.stream()
            .map(item -> new SelecaoEnsaioItemResponse(
                item.getFotoId(),
                item.getNumeroGaleria(),
                item.getNomeArquivo(),
                item.getTipo().name(),
                item.getOrdem(),
                item.getValorUnitario(),
                item.isEditada(),
                item.getEditadaEm(),
                java.util.Optional.ofNullable(fotos.get(item.getFotoId()))
                    .map(fotoMapper::toResponse)
                    .orElse(null)))
            .toList();
        return new SelecaoEnsaioResponse(
            selecao.getId(),
            selecao.getAgendamentoId(),
            selecao.getNumero(),
            selecao.getStatus().name(),
            selecao.getObservacaoCliente(),
            selecao.getEnviadaEm(),
            selecao.getIniciadaEm(),
            selecao.getConcluidaEm(),
            selecao.getPublicadaEm(),
            selecao.getOriginaisBaixadosEm(),
            itens.stream().filter(i -> i.getTipo() == TipoItemSelecao.PACOTE).count(),
            itens.stream().filter(i -> i.getTipo() == TipoItemSelecao.EXTRA).count(),
            itensResponse);
    }
}
