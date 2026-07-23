package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.ecommerce.api.AdminCompraDetalheResponse;
import com.photoizer.crm.ecommerce.api.AdminComprasRelatorioResponse;
import com.photoizer.crm.ecommerce.api.CalculoCarrinhoResponse;
import com.photoizer.crm.ecommerce.api.CalculoItemResponse;
import com.photoizer.crm.ecommerce.event.CompraExtraConfirmadaEvent;
import com.photoizer.crm.ecommerce.event.CompraExtraCriadaEvent;
import com.photoizer.crm.foto.api.FotoEnsaioResponse;
import com.photoizer.crm.ecommerce.model.CompraExtra;
import com.photoizer.crm.ecommerce.model.ItemCarrinho;
import com.photoizer.crm.ecommerce.model.MetodoPagamento;
import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import com.photoizer.crm.ecommerce.model.Favorito;
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import com.photoizer.crm.ecommerce.repository.FavoritoRepository;
import com.photoizer.crm.ecommerce.repository.ItemCarrinhoRepository;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import com.photoizer.crm.shared.storage.FileStorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Transactional
public class EcommerceService {

    private static final BigDecimal PERCENTUAL_COMISSAO_PADRAO = BigDecimal.TEN;

    private final AgendamentoRepository agendamentoRepository;
    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final CompraExtraRepository compraExtraRepository;
    private final ItemCarrinhoRepository itemCarrinhoRepository;
    private final FavoritoRepository favoritoRepository;
    private final ConfiguracaoService configuracaoService;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    public EcommerceService(AgendamentoRepository agendamentoRepository,
                            FotoEnsaioRepository fotoEnsaioRepository,
                            CompraExtraRepository compraExtraRepository,
                            ItemCarrinhoRepository itemCarrinhoRepository,
                            FavoritoRepository favoritoRepository,
                            ConfiguracaoService configuracaoService,
                            FileStorageService fileStorageService,
                            ApplicationEventPublisher eventPublisher) {
        this.agendamentoRepository = agendamentoRepository;
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.compraExtraRepository = compraExtraRepository;
        this.itemCarrinhoRepository = itemCarrinhoRepository;
        this.favoritoRepository = favoritoRepository;
        this.configuracaoService = configuracaoService;
        this.fileStorageService = fileStorageService;
        this.eventPublisher = eventPublisher;
    }

    // ==================== Wishlist / Favoritos (RF008) ====================

    public void adicionarFavorito(UUID token, UUID sessionId, UUID fotoId) {
        var agendamento = buscarAgendamentoPorToken(token);
        if (favoritoRepository.findBySessionIdAndFotoId(sessionId, fotoId).isEmpty()) {
            favoritoRepository.save(Favorito.builder()
                .agendamentoId(agendamento.getId())
                .fotoId(fotoId)
                .sessionId(sessionId)
                .build());
        }
    }

    public void removerFavorito(UUID token, UUID sessionId, UUID fotoId) {
        buscarAgendamentoPorToken(token);
        favoritoRepository.deleteBySessionIdAndFotoId(sessionId, fotoId);
    }

    @Transactional(readOnly = true)
    public List<UUID> listarFavoritos(UUID token, UUID sessionId) {
        var agendamento = buscarAgendamentoPorToken(token);
        return favoritoRepository.findBySessionIdAndAgendamentoIdOrderByCreatedAtAsc(sessionId, agendamento.getId())
            .stream().map(Favorito::getFotoId).toList();
    }

    @Transactional(readOnly = true)
    public Agendamento buscarAgendamentoPorToken(UUID token) {
        return agendamentoRepository.findByTokenGaleria(token)
            .orElseThrow(() -> new RuntimeException("Galeria não encontrada"));
    }

    @Transactional(readOnly = true)
    public Agendamento buscarAgendamento(UUID token) {
        return buscarAgendamentoPorToken(token);
    }

    @Transactional(readOnly = true)
    public BigDecimal getValorUnitarioFotoExtra() {
        return configuracaoService.getValorDecimal("valorUnitarioFotoExtra", new BigDecimal("15.00"));
    }

    @Transactional(readOnly = true)
    public List<FotoEnsaio> listarFotosPublicadas(UUID token) {
        var agendamento = buscarAgendamentoPorToken(token);
        return fotoEnsaioRepository.findByAgendamentoIdAndStatusOrderByOrdemAsc(
            agendamento.getId(), StatusFoto.PUBLICADA);
    }

    public List<FotoEnsaio> selecionarFotos(UUID token, List<UUID> fotoIds, boolean selecionada) {
        var agendamento = buscarAgendamentoPorToken(token);
        var pacote = agendamento.getPacote();

        if (selecionada) {
            var atualmenteSelecionadas = fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(
                agendamento.getId()).stream()
                .filter(FotoEnsaio::isSelecionadaPacote)
                .count();

            if (atualmenteSelecionadas + fotoIds.size() > pacote.getQuantidadeFotos()) {
                throw new IllegalArgumentException(
                    "Limite de " + pacote.getQuantidadeFotos() + " fotos do pacote excedido");
            }
        }

        var fotos = fotoEnsaioRepository.findAllById(fotoIds);
        for (var foto : fotos) {
            if (!foto.getAgendamentoId().equals(agendamento.getId())) continue;
            foto.setSelecionadaPacote(selecionada);
        }
        return fotoEnsaioRepository.saveAll(fotos);
    }

    @Transactional(readOnly = true)
    public CalculoCarrinhoResponse calcularCarrinho(UUID token, UUID sessionId) {
        var agendamento = buscarAgendamentoPorToken(token);
        var itensCarrinho = itemCarrinhoRepository.findBySessionIdAndAgendamentoIdOrderByCreatedAtAsc(sessionId, agendamento.getId());
        var valorUnitario = getValorUnitarioFotoExtra();
        var quantidade = itensCarrinho.size();
        var subtotal = valorUnitario.multiply(BigDecimal.valueOf(quantidade)).setScale(2, RoundingMode.HALF_UP);

        var itens = itensCarrinho.stream()
            .map(ItemCarrinho::getFotoId)
            .map(fotoEnsaioRepository::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(foto -> new CalculoItemResponse(foto.getId(), foto.getFileName(), valorUnitario))
            .toList();

        return new CalculoCarrinhoResponse(itens, quantidade, valorUnitario, subtotal, subtotal);
    }

    public CompraExtra checkout(UUID token, UUID sessionId, MetodoPagamento metodoPagamento) {
        var agendamento = buscarAgendamentoPorToken(token);
        var itensCarrinho = itemCarrinhoRepository.findBySessionIdAndAgendamentoIdOrderByCreatedAtAsc(sessionId, agendamento.getId());

        if (itensCarrinho.isEmpty()) {
            throw new IllegalArgumentException("Carrinho vazio");
        }

        var fotoIds = itensCarrinho.stream().map(ItemCarrinho::getFotoId).toList();
        var valorUnitario = getValorUnitarioFotoExtra();
        var valorTotal = valorUnitario.multiply(BigDecimal.valueOf(fotoIds.size()))
            .setScale(2, RoundingMode.HALF_UP);

        var compra = CompraExtra.builder()
            .agendamentoId(agendamento.getId())
            .valorTotal(valorTotal)
            .quantidadeFotos(fotoIds.size())
            .metodoPagamento(metodoPagamento)
            .status(StatusCompraExtra.AGUARDANDO_COMPROVANTE)
            .build();
        compra = compraExtraRepository.save(compra);

        var fotos = fotoEnsaioRepository.findAllById(fotoIds);
        for (var foto : fotos) {
            if (!foto.getAgendamentoId().equals(agendamento.getId())) continue;
            foto.setCompraExtraId(compra.getId());
        }
        fotoEnsaioRepository.saveAll(fotos);

        itemCarrinhoRepository.deleteBySessionIdAndAgendamentoId(sessionId, agendamento.getId());

        eventPublisher.publishEvent(new CompraExtraCriadaEvent(
            agendamento.getId(), compra.getId(), valorTotal, fotoIds.size()));

        return compra;
    }

    public void adicionarAoCarrinho(UUID token, UUID sessionId, UUID fotoId) {
        var agendamento = buscarAgendamentoPorToken(token);
        var jaExiste = itemCarrinhoRepository.findBySessionIdAndAgendamentoIdOrderByCreatedAtAsc(sessionId, agendamento.getId())
            .stream().anyMatch(item -> item.getFotoId().equals(fotoId));
        if (!jaExiste) {
            var item = ItemCarrinho.builder()
                .agendamentoId(agendamento.getId())
                .fotoId(fotoId)
                .sessionId(sessionId)
                .build();
            itemCarrinhoRepository.save(item);
        }
    }

    public void removerDoCarrinho(UUID token, UUID sessionId, UUID fotoId) {
        var agendamento = buscarAgendamentoPorToken(token);
        itemCarrinhoRepository.deleteBySessionIdAndAgendamentoIdAndFotoId(sessionId, agendamento.getId(), fotoId);
    }

    @Transactional(readOnly = true)
    public List<ItemCarrinho> listarCarrinho(UUID token, UUID sessionId) {
        var agendamento = buscarAgendamentoPorToken(token);
        return itemCarrinhoRepository.findBySessionIdAndAgendamentoIdOrderByCreatedAtAsc(sessionId, agendamento.getId());
    }

    @Transactional(readOnly = true)
    public int contarCarrinho(UUID token, UUID sessionId) {
        var agendamento = buscarAgendamentoPorToken(token);
        return itemCarrinhoRepository.countBySessionIdAndAgendamentoId(sessionId, agendamento.getId());
    }

    public CompraExtra uploadComprovante(UUID token, UUID compraExtraId, MultipartFile comprovante) {
        var agendamento = buscarAgendamentoPorToken(token);
        var compra = compraExtraRepository.findById(compraExtraId)
            .orElseThrow(() -> new RuntimeException("Compra não encontrada"));

        if (!compra.getAgendamentoId().equals(agendamento.getId())) {
            throw new RuntimeException("Compra não pertence a este agendamento");
        }

        var caminho = fileStorageService.salvarEmSubdiretorio(comprovante, agendamento.getId(), "comprovante_extra");
        compra.setUrlComprovante(caminho);
        compra.setStatus(StatusCompraExtra.AGUARDANDO_CONFIRMACAO);

        return compraExtraRepository.save(compra);
    }

    public void confirmarPagamento(UUID compraExtraId) {
        var compra = compraExtraRepository.findById(compraExtraId)
            .orElseThrow(() -> new RuntimeException("Compra não encontrada"));

        compra.setStatus(StatusCompraExtra.PAGA);
        compra.setDataPagamento(LocalDateTime.now());
        compraExtraRepository.save(compra);

        var fotos = fotoEnsaioRepository.findAll().stream()
            .filter(f -> compra.getId().equals(f.getCompraExtraId()))
            .toList();
        for (var foto : fotos) {
            foto.setStatus(StatusFoto.PAGA);
        }
        fotoEnsaioRepository.saveAll(fotos);

        eventPublisher.publishEvent(new CompraExtraConfirmadaEvent(
            compra.getAgendamentoId(), compra.getId(), compra.getValorTotal()));
    }

    @Transactional(readOnly = true)
    public boolean isDownloadPermitido(FotoEnsaio foto) {
        return foto.isSelecionadaPacote() || foto.getStatus() == StatusFoto.PAGA;
    }

    @Transactional(readOnly = true)
    public List<FotoEnsaio> getDownloadableFotos(UUID token) {
        var agendamento = buscarAgendamentoPorToken(token);
        var fotos = fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(agendamento.getId());
        return fotos.stream().filter(this::isDownloadPermitido).toList();
    }

    public Path downloadFoto(UUID token, UUID fotoId) {
        var agendamento = buscarAgendamentoPorToken(token);
        var foto = fotoEnsaioRepository.findById(fotoId)
            .orElseThrow(() -> new RuntimeException("Foto não encontrada"));

        if (!foto.getAgendamentoId().equals(agendamento.getId())) {
            throw new RuntimeException("Foto não pertence a este agendamento");
        }
        if (!isDownloadPermitido(foto)) {
            throw new RuntimeException("Foto não está disponível para download");
        }

        foto.setDataDownload(LocalDateTime.now());
        fotoEnsaioRepository.save(foto);

        return Path.of(foto.getOriginalPath());
    }

    @Transactional(readOnly = true)
    public List<FotoEnsaio> listarFotosPorAgendamento(UUID agendamentoId) {
        return fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId);
    }

    @Transactional(readOnly = true)
    public List<CompraExtra> listarComprasPorAgendamento(UUID agendamentoId) {
        return compraExtraRepository.findByAgendamentoId(agendamentoId);
    }

    @Transactional(readOnly = true)
    public List<CompraExtra> listarComprasPorToken(UUID token) {
        var agendamento = buscarAgendamentoPorToken(token);
        return compraExtraRepository.findByAgendamentoId(agendamento.getId());
    }

    @Transactional(readOnly = true)
    public AdminCompraDetalheResponse buscarCompraDetalhePorToken(UUID token, UUID compraId) {
        var agendamento = buscarAgendamentoPorToken(token);
        var compra = compraExtraRepository.findById(compraId)
            .orElseThrow(() -> new RuntimeException("Compra não encontrada"));
        if (!compra.getAgendamentoId().equals(agendamento.getId())) {
            throw new RuntimeException("Compra não pertence a esta galeria");
        }
        var fotos = fotoEnsaioRepository.findAll().stream()
            .filter(f -> compra.getId().equals(f.getCompraExtraId()))
            .map(FotoEnsaioResponse::of)
            .toList();
        return new AdminCompraDetalheResponse(
            compra.getId(), compra.getAgendamentoId(), compra.getValorTotal(),
            compra.getStatus().name(), compra.getUrlComprovante(), compra.getDataPagamento(),
            compra.getQuantidadeFotos(),
            compra.getMetodoPagamento() != null ? compra.getMetodoPagamento().name() : null,
            fotos, compra.getCreatedAt(), compra.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<CompraExtra> listarTodasCompras() {
        return compraExtraRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Transactional(readOnly = true)
    public List<CompraExtra> listarComprasPorStatus(StatusCompraExtra status) {
        return compraExtraRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public Page<CompraExtra> listarComprasPaginado(String status, LocalDateTime dataInicio, LocalDateTime dataFim, int page, int perPage) {
        var pageable = PageRequest.of(page - 1, perPage, Sort.by(Sort.Direction.DESC, "createdAt"));
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
            .orElseThrow(() -> new RuntimeException("Compra não encontrada"));
        var agendamento = agendamentoRepository.findById(compra.getAgendamentoId())
            .orElse(null);
        var fotos = fotoEnsaioRepository.findAll().stream()
            .filter(f -> compra.getId().equals(f.getCompraExtraId()))
            .map(FotoEnsaioResponse::of)
            .toList();
        return new AdminCompraDetalheResponse(
            compra.getId(), compra.getAgendamentoId(), compra.getValorTotal(),
            compra.getStatus().name(), compra.getUrlComprovante(), compra.getDataPagamento(),
            compra.getQuantidadeFotos(),
            compra.getMetodoPagamento() != null ? compra.getMetodoPagamento().name() : null,
            fotos, compra.getCreatedAt(), compra.getUpdatedAt()
        );
    }

    public void cancelarCompra(UUID compraId) {
        var compra = compraExtraRepository.findById(compraId)
            .orElseThrow(() -> new RuntimeException("Compra não encontrada"));
        if (compra.getStatus() == StatusCompraExtra.PAGA) {
            throw new IllegalStateException("Compra já paga não pode ser cancelada");
        }
        compra.setStatus(StatusCompraExtra.CANCELADA);
        compraExtraRepository.save(compra);
        var fotos = fotoEnsaioRepository.findAll().stream()
            .filter(f -> compra.getId().equals(f.getCompraExtraId()))
            .toList();
        for (var foto : fotos) {
            foto.setCompraExtraId(null);
        }
        fotoEnsaioRepository.saveAll(fotos);
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
            .orElseThrow(() -> new RuntimeException("Foto não encontrada"));
        if (!foto.getAgendamentoId().equals(agendamentoId)) {
            throw new RuntimeException("Foto não pertence a este agendamento");
        }
        foto.setSelecionadaPacote(selecionada);
        return fotoEnsaioRepository.save(foto);
    }

    public UUID regerarToken(UUID agendamentoId) {
        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new RuntimeException("Agendamento não encontrado"));
        var novoToken = UUID.randomUUID();
        agendamento.setTokenGaleria(novoToken);
        agendamentoRepository.save(agendamento);
        return novoToken;
    }

    public Path downloadZip(UUID token) {
        var agendamento = buscarAgendamentoPorToken(token);
        var fotos = getDownloadableFotos(token);

        if (fotos.isEmpty()) {
            throw new RuntimeException("Nenhuma foto disponível para download");
        }

        try {
            var tempDir = Files.createTempDirectory("galeria_");
            var zipPath = tempDir.resolve("fotos_" + agendamento.getId() + ".zip");

            try (var zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
                for (var foto : fotos) {
                    var originalPath = Path.of(foto.getOriginalPath());
                    if (Files.exists(originalPath)) {
                        var entryName = foto.getOrdem() + "_" + foto.getFileName();
                        zos.putNextEntry(new ZipEntry(entryName));
                        Files.copy(originalPath, zos);
                        zos.closeEntry();
                    }
                    foto.setDataDownload(LocalDateTime.now());
                }
                fotoEnsaioRepository.saveAll(fotos);
            }

            return zipPath;
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar arquivo ZIP", e);
        }
    }
}
