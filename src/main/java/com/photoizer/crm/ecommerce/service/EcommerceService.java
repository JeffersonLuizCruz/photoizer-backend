package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.api.AdminCompraDetalheResponse;
import com.photoizer.crm.ecommerce.api.AdminComprasRelatorioResponse;
import com.photoizer.crm.ecommerce.event.CompraExtraCriadaEvent;
import com.photoizer.crm.ecommerce.event.CompraExtraFotosAssociadasEvent;
import com.photoizer.crm.ecommerce.event.FotosSelecionadasEvent;
import com.photoizer.crm.ecommerce.event.TokenGaleriaRegeneradoEvent;
import com.photoizer.crm.ecommerce.exception.CarrinhoVazioException;
import com.photoizer.crm.ecommerce.exception.CompraNaoEncontradaException;
import com.photoizer.crm.ecommerce.exception.FotoIndisponivelException;
import com.photoizer.crm.ecommerce.exception.FotoJaSelecionadaException;
import com.photoizer.crm.ecommerce.exception.FotoNaoEncontradaException;
import com.photoizer.crm.ecommerce.exception.GaleriaNaoEncontradaException;
import com.photoizer.crm.ecommerce.exception.LimitePacoteExcedidoException;
import com.photoizer.crm.ecommerce.model.CompraExtra;
import com.photoizer.crm.ecommerce.model.MetodoPagamento;
import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import com.photoizer.crm.foto.api.FotoEnsaioResponse;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import com.photoizer.crm.shared.storage.FileStorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
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

    public EcommerceService(FotoEnsaioRepository fotoEnsaioRepository,
                            CompraExtraRepository compraExtraRepository,
                            FileStorageService fileStorageService,
                            ApplicationEventPublisher eventPublisher,
                            GaleriaQueryService galeriaQueryService) {
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.compraExtraRepository = compraExtraRepository;
        this.fileStorageService = fileStorageService;
        this.eventPublisher = eventPublisher;
        this.galeriaQueryService = galeriaQueryService;
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

    // ==================== Selecao de Fotos ====================

    public List<FotoEnsaio> selecionarFotos(UUID token, List<UUID> fotoIds, boolean selecionada) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        var fotos = fotoEnsaioRepository.findAllById(fotoIds);

        if (selecionada) {
            var fotosSolicitadas = fotos.stream()
                .filter(f -> f.getAgendamentoId().equals(agendamento.getId()))
                .toList();
            var limitePacote = agendamento.getPacote().getQuantidadeFotos();
            var jaSelecionadas = fotoEnsaioRepository.countSelecionadasPacoteByAgendamentoId(agendamento.getId());
            var novasSelecoes = fotosSolicitadas.stream().filter(f -> !f.isSelecionadaPacote()).count();
            if (jaSelecionadas + novasSelecoes > limitePacote) {
                throw new LimitePacoteExcedidoException(limitePacote);
            }
        } else {
            var bloqueadas = fotos.stream()
                .filter(f -> f.getAgendamentoId().equals(agendamento.getId()))
                .filter(f -> f.isSelecionadaPacote() && f.getDataDownload() != null)
                .findAny();
            if (bloqueadas.isPresent()) {
                throw new FotoJaSelecionadaException("Foto ja baixada nao pode ser removida do pacote");
            }
        }

        var fotoIdsValidas = fotos.stream()
            .filter(f -> f.getAgendamentoId().equals(agendamento.getId()))
            .map(FotoEnsaio::getId)
            .toList();

        eventPublisher.publishEvent(new FotosSelecionadasEvent(
            agendamento.getId(), fotoIdsValidas, selecionada));

        return fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(agendamento.getId());
    }

    // ==================== Checkout ====================

    public CompraExtra checkout(UUID token, UUID sessionId, MetodoPagamento metodoPagamento,
                                CarrinhoService carrinhoService) {
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

    @Transactional(readOnly = true)
    public List<CompraExtra> listarComprasPorAgendamento(UUID agendamentoId) {
        return compraExtraRepository.findByAgendamentoId(agendamentoId);
    }

    @Transactional(readOnly = true)
    public List<CompraExtra> listarComprasPorToken(UUID token) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        return compraExtraRepository.findByAgendamentoId(agendamento.getId());
    }

    @Transactional(readOnly = true)
    public AdminCompraDetalheResponse buscarCompraDetalhePorToken(UUID token, UUID compraId) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        var compra = compraExtraRepository.findById(compraId)
            .orElseThrow(() -> new CompraNaoEncontradaException(compraId));
        if (!compra.getAgendamentoId().equals(agendamento.getId())) {
            throw new CompraNaoEncontradaException(compraId);
        }
        var fotos = fotoEnsaioRepository.findByCompraExtraId(compra.getId()).stream()
            .map(FotoEnsaioResponse::ofPublic)
            .toList();
        return new AdminCompraDetalheResponse(
            compra.getId(), compra.getAgendamentoId(), compra.getValorTotal(),
            compra.getStatus().name(), null, compra.getDataPagamento(),
            compra.getQuantidadeFotos(),
            compra.getMetodoPagamento() != null ? compra.getMetodoPagamento().name() : null,
            fotos, compra.getAuditInfo().getCreatedAt(), compra.getAuditInfo().getUpdatedAt(),
            compra.getMotivoRecusa()
        );
    }

    @Transactional(readOnly = true)
    public Path buscarComprovantePath(UUID token, UUID compraId) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        var compra = compraExtraRepository.findById(compraId)
            .orElseThrow(() -> new CompraNaoEncontradaException(compraId));
        if (!compra.getAgendamentoId().equals(agendamento.getId())) {
            throw new CompraNaoEncontradaException(compraId);
        }
        return compra.getUrlComprovante() != null ? Path.of(compra.getUrlComprovante()) : null;
    }

    @Transactional(readOnly = true)
    public Path buscarComprovantePathPorId(UUID compraId) {
        var compra = compraExtraRepository.findById(compraId)
            .orElseThrow(() -> new CompraNaoEncontradaException(compraId));
        return compra.getUrlComprovante() != null ? Path.of(compra.getUrlComprovante()) : null;
    }

    @Transactional(readOnly = true)
    public List<CompraExtra> listarTodasCompras() {
        return compraExtraRepository.findAll(Sort.by(Sort.Direction.DESC, "auditInfo.createdAt"));
    }

    @Transactional(readOnly = true)
    public List<CompraExtra> listarComprasPorStatus(StatusCompraExtra status) {
        return compraExtraRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public Page<CompraExtra> listarComprasPaginado(String status, LocalDateTime dataInicio, LocalDateTime dataFim, int page, int perPage) {
        var pageable = PageRequest.of(page - 1, perPage, Sort.by(Sort.Direction.DESC, "auditInfo.createdAt"));
        if (status != null && !status.isBlank()) {
            var statusEnum = StatusCompraExtra.valueOf(status.toUpperCase());
            if (dataInicio != null && dataFim != null) {
                return compraExtraRepository.findByStatusAndPeriodo(statusEnum, dataInicio, dataFim, pageable);
            }
            return compraExtraRepository.findByStatus(statusEnum, pageable);
        }
        if (dataInicio != null && dataFim != null) {
            return compraExtraRepository.findByPeriodo(dataInicio, dataFim, pageable);
        }
        return compraExtraRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public AdminCompraDetalheResponse buscarCompraDetalhe(UUID compraId) {
        var compra = compraExtraRepository.findById(compraId)
            .orElseThrow(() -> new CompraNaoEncontradaException(compraId));
        var fotos = fotoEnsaioRepository.findByCompraExtraId(compra.getId()).stream()
            .map(FotoEnsaioResponse::of)
            .toList();
        var comprovanteUrl = compra.getUrlComprovante() != null
            ? "/api/v1/admin/ecommerce/compras/" + compra.getId() + "/comprovante"
            : null;
        return new AdminCompraDetalheResponse(
            compra.getId(), compra.getAgendamentoId(), compra.getValorTotal(),
            compra.getStatus().name(), comprovanteUrl, compra.getDataPagamento(),
            compra.getQuantidadeFotos(),
            compra.getMetodoPagamento() != null ? compra.getMetodoPagamento().name() : null,
            fotos, compra.getAuditInfo().getCreatedAt(), compra.getAuditInfo().getUpdatedAt(), compra.getMotivoRecusa()
        );
    }

    public void cancelarCompra(UUID compraId) {
        cancelarCompra(compraId, null);
    }

    public void cancelarCompra(UUID compraId, String motivoRecusa) {
        var compra = compraExtraRepository.findById(compraId)
            .orElseThrow(() -> new CompraNaoEncontradaException(compraId));
        if (!compra.getStatus().podeSerCancelada()) {
            throw new com.photoizer.crm.ecommerce.exception.CompraJaPagaException();
        }
        compra.setStatus(StatusCompraExtra.CANCELADA);
        if (motivoRecusa != null && !motivoRecusa.isBlank()) {
            compra.setMotivoRecusa(motivoRecusa.trim());
        }
        compraExtraRepository.save(compra);

        eventPublisher.publishEvent(new com.photoizer.crm.ecommerce.event.CompraExtraCanceladaEvent(
            compra.getId(), compra.getAgendamentoId()));
    }

    @Transactional(readOnly = true)
    public AdminComprasRelatorioResponse gerarRelatorio() {
        return new AdminComprasRelatorioResponse(
            (int) compraExtraRepository.count(),
            compraExtraRepository.countByStatus(StatusCompraExtra.AGUARDANDO_COMPROVANTE),
            compraExtraRepository.countByStatus(StatusCompraExtra.AGUARDANDO_CONFIRMACAO),
            compraExtraRepository.countByStatus(StatusCompraExtra.PAGA),
            compraExtraRepository.countByStatus(StatusCompraExtra.CANCELADA),
            compraExtraRepository.totalPorStatus(StatusCompraExtra.PAGA),
            compraExtraRepository.totalPorStatus(StatusCompraExtra.AGUARDANDO_COMPROVANTE)
                .add(compraExtraRepository.totalPorStatus(StatusCompraExtra.AGUARDANDO_CONFIRMACAO))
        );
    }

    public FotoEnsaio overrideSelecao(UUID agendamentoId, UUID fotoId, boolean selecionada) {
        var foto = fotoEnsaioRepository.findById(fotoId)
            .orElseThrow(() -> new FotoNaoEncontradaException(fotoId));
        if (!foto.getAgendamentoId().equals(agendamentoId)) {
            throw new FotoIndisponivelException("Foto nao pertence a este agendamento");
        }

        eventPublisher.publishEvent(new FotosSelecionadasEvent(
            agendamentoId, List.of(fotoId), selecionada));

        return fotoEnsaioRepository.findById(fotoId).orElse(foto);
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
