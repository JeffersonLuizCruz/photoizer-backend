package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.ecommerce.model.CompraExtra;
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import com.photoizer.crm.shared.storage.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
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
    private final ConfiguracaoService configuracaoService;
    private final FileStorageService fileStorageService;

    public EcommerceService(AgendamentoRepository agendamentoRepository,
                            FotoEnsaioRepository fotoEnsaioRepository,
                            CompraExtraRepository compraExtraRepository,
                            ConfiguracaoService configuracaoService,
                            FileStorageService fileStorageService) {
        this.agendamentoRepository = agendamentoRepository;
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.compraExtraRepository = compraExtraRepository;
        this.configuracaoService = configuracaoService;
        this.fileStorageService = fileStorageService;
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
            agendamento.getId(), "PUBLICADA");
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

    public CompraExtra checkout(UUID token, List<UUID> fotoIds) {
        var agendamento = buscarAgendamentoPorToken(token);
        var valorUnitario = getValorUnitarioFotoExtra();
        var valorTotal = valorUnitario.multiply(BigDecimal.valueOf(fotoIds.size()))
            .setScale(2, RoundingMode.HALF_UP);

        var compra = CompraExtra.builder()
            .agendamentoId(agendamento.getId())
            .valorTotal(valorTotal)
            .status("AGUARDANDO_COMPROVANTE")
            .build();
        compra = compraExtraRepository.save(compra);

        var fotos = fotoEnsaioRepository.findAllById(fotoIds);
        for (var foto : fotos) {
            if (!foto.getAgendamentoId().equals(agendamento.getId())) continue;
            foto.setCompraExtraId(compra.getId());
        }
        fotoEnsaioRepository.saveAll(fotos);

        return compra;
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
        compra.setStatus("AGUARDANDO_CONFIRMACAO");

        return compraExtraRepository.save(compra);
    }

    public void confirmarPagamento(UUID compraExtraId) {
        var compra = compraExtraRepository.findById(compraExtraId)
            .orElseThrow(() -> new RuntimeException("Compra não encontrada"));

        compra.setStatus("PAGA");
        compra.setDataPagamento(LocalDateTime.now());
        compraExtraRepository.save(compra);

        var fotos = fotoEnsaioRepository.findAll().stream()
            .filter(f -> compra.getId().equals(f.getCompraExtraId()))
            .toList();
        for (var foto : fotos) {
            foto.setStatus("PAGA");
        }
        fotoEnsaioRepository.saveAll(fotos);
    }

    @Transactional(readOnly = true)
    public boolean isDownloadPermitido(FotoEnsaio foto) {
        return foto.isSelecionadaPacote() || "PAGA".equals(foto.getStatus());
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
        return compraExtraRepository.findAll().stream()
            .filter(c -> c.getAgendamentoId().equals(agendamentoId))
            .toList();
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
                        zos.putNextEntry(new ZipEntry(foto.getFileName()));
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
