package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.event.CompraExtraCriadaEvent;
import com.photoizer.crm.ecommerce.event.CompraExtraFotosAssociadasEvent;
import com.photoizer.crm.ecommerce.event.TokenGaleriaRegeneradoEvent;
import com.photoizer.crm.ecommerce.exception.CarrinhoVazioException;
import com.photoizer.crm.ecommerce.exception.CompraNaoEncontradaException;
import com.photoizer.crm.ecommerce.exception.FotoIndisponivelException;
import com.photoizer.crm.ecommerce.model.CompraExtra;
import com.photoizer.crm.ecommerce.model.MetodoPagamento;
import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import com.photoizer.crm.shared.storage.FileStorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * PATTERN: Facade Pattern (refatorado)
 * Apos a extracao dos services especializados, o EcommerceService agora atua como
 * um orquestrador fino que delega para CarrinhoService, FavoritoService,
 * DownloadService, PagamentoExtraService e GaleriaQueryService.
 * Restam apenas: checkout, selecao de fotos, queries admin, override selecao,
 * regenerar token e upload de comprovante - que sao operacoes de orquestracao.
 *
 * MODULITH: Escritas em entidades de outros modulos sao feitas via eventos.
 * O módulo ecommerce NÃO deve escrever diretamente em FotoEnsaio ou Agendamento.
 */
@Service
@Transactional
public class EcommerceService {

    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final CompraExtraRepository compraExtraRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;
    private final GaleriaQueryService galeriaQueryService;
    private final CarrinhoService carrinhoService;
    private final SelecaoFotosService selecaoFotosService;

    public EcommerceService(FotoEnsaioRepository fotoEnsaioRepository,
                            CompraExtraRepository compraExtraRepository,
                            FileStorageService fileStorageService,
                            ApplicationEventPublisher eventPublisher,
                            GaleriaQueryService galeriaQueryService,
                            CarrinhoService carrinhoService,
                            SelecaoFotosService selecaoFotosService) {
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.compraExtraRepository = compraExtraRepository;
        this.fileStorageService = fileStorageService;
        this.eventPublisher = eventPublisher;
        this.galeriaQueryService = galeriaQueryService;
        this.carrinhoService = carrinhoService;
        this.selecaoFotosService = selecaoFotosService;
    }

    // ==================== Galeria (delegado para GaleriaQueryService) ====================

    @Transactional(readOnly = true)
    public List<FotoEnsaio> listarFotosPublicadas(UUID token) {
        return galeriaQueryService.listarFotosPublicadas(token);
    }

    @Transactional(readOnly = true)
    public BigDecimal getValorUnitarioFotoExtra(UUID agendamentoId) {
        return galeriaQueryService.getValorUnitarioFotoExtra(agendamentoId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getValorUnitarioFotoExtraPorToken(UUID token) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        return galeriaQueryService.getValorUnitarioFotoExtra(agendamento.getId());
    }

    @Transactional(readOnly = true)
    public List<FotoEnsaio> listarFotosPorAgendamento(UUID agendamentoId) {
        return galeriaQueryService.listarFotosPorAgendamento(agendamentoId);
    }

    // ==================== Selecao de Fotos (delegado para SelecaoFotosService) ====================

    public List<FotoEnsaio> selecionarFotos(UUID token, List<UUID> fotoIds, boolean selecionada) {
        return selecaoFotosService.selecionarFotos(token, fotoIds, selecionada);
    }

    // ==================== Checkout ====================

    public CompraExtra checkout(UUID token, UUID sessionId, MetodoPagamento metodoPagamento) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        var itensCarrinho = carrinhoService.listarCarrinho(token, sessionId);

        if (itensCarrinho.isEmpty()) {
            throw new CarrinhoVazioException();
        }

        var fotos = fotoEnsaioRepository.findAllById(
                itensCarrinho.stream().map(com.photoizer.crm.ecommerce.model.ItemCarrinho::getFotoId).toList())
            .stream()
            .filter(f -> f.getAgendamentoId().equals(agendamento.getId()))
            .toList();

        if (fotos.isEmpty()) {
            throw new FotoIndisponivelException("Nenhuma foto valida no carrinho");
        }

        if (fotos.stream().anyMatch(FotoEnsaio::isSelecionadaPacote)) {
            throw new FotoIndisponivelException("Fotos ja incluidas no pacote nao podem ser cobradas como extras");
        }

        var valorUnitario = galeriaQueryService.getValorUnitarioFotoExtra(agendamento.getId());
        var valorTotal = valorUnitario.multiply(BigDecimal.valueOf(fotos.size()))
            .setScale(2, RoundingMode.HALF_UP);

        var compra = CompraExtra.builder()
            .agendamentoId(agendamento.getId())
            .valorTotal(valorTotal)
            .quantidadeFotos(fotos.size())
            .metodoPagamento(metodoPagamento)
            .status(StatusCompraExtra.AGUARDANDO_COMPROVANTE)
            .build();
        compra = compraExtraRepository.save(compra);

        var fotoIds = fotos.stream().map(FotoEnsaio::getId).toList();
        eventPublisher.publishEvent(new CompraExtraFotosAssociadasEvent(
            compra.getId(), agendamento.getId(), fotoIds));

        carrinhoService.limparCarrinho(token, sessionId);

        eventPublisher.publishEvent(new CompraExtraCriadaEvent(
            agendamento.getId(), compra.getId(), valorTotal, fotos.size()));

        return compra;
    }

    // ==================== Upload Comprovante ====================

    public CompraExtra uploadComprovante(UUID token, UUID compraExtraId, MultipartFile comprovante) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        var compra = compraExtraRepository.findById(compraExtraId)
            .orElseThrow(() -> new CompraNaoEncontradaException(compraExtraId));

        if (!compra.getAgendamentoId().equals(agendamento.getId())) {
            throw new CompraNaoEncontradaException(compraExtraId);
        }

        var caminho = fileStorageService.salvarEmSubdiretorio(comprovante, agendamento.getId(), "comprovante_extra");
        compra.setUrlComprovante(caminho);
        compra.setStatus(StatusCompraExtra.AGUARDANDO_CONFIRMACAO);

        return compraExtraRepository.save(compra);
    }

    public FotoEnsaio overrideSelecao(UUID agendamentoId, UUID fotoId, boolean selecionada) {
        return selecaoFotosService.overrideSelecao(agendamentoId, fotoId, selecionada);
    }

    public UUID regerarToken(UUID agendamentoId) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorId(agendamentoId);
        var novoToken = UUID.randomUUID();
        var expiracao = LocalDateTime.now().plusDays(15);

        eventPublisher.publishEvent(new TokenGaleriaRegeneradoEvent(
            agendamentoId, novoToken, expiracao));

        return novoToken;
    }

}
