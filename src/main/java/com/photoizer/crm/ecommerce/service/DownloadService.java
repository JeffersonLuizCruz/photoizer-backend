package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.event.FotoDownloadEvent;
import com.photoizer.crm.ecommerce.exception.FotoIndisponivelException;
import com.photoizer.crm.ecommerce.exception.FotoNaoEncontradaException;
import com.photoizer.crm.ecommerce.exception.GaleriaNaoEncontradaException;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.VersaoFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * PATTERN: Facade Pattern
 * Encapsula todas as operações de download de fotos (individual e ZIP).
 *
 * MODULITH: Escritas em FotoEnsaio são feitas via eventos.
 * O módulo ecommerce NÃO deve escrever diretamente em FotoEnsaio.
 */
@Service
@Transactional
public class DownloadService {

    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final GaleriaQueryService galeriaQueryService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.photoizer.crm.shared.storage.StoragePathResolver pathResolver;

    public DownloadService(FotoEnsaioRepository fotoEnsaioRepository,
                           GaleriaQueryService galeriaQueryService,
                           ApplicationEventPublisher eventPublisher,
                           com.photoizer.crm.shared.storage.StoragePathResolver pathResolver) {
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.galeriaQueryService = galeriaQueryService;
        this.eventPublisher = eventPublisher;
        this.pathResolver = pathResolver;
    }

    public record ArquivoDownload(Path path, String nome) {}

    public ArquivoDownload downloadFoto(UUID token, UUID fotoId) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        var foto = fotoEnsaioRepository.findById(fotoId)
            .orElseThrow(() -> new FotoNaoEncontradaException(fotoId));

        if (!foto.getAgendamentoId().equals(agendamento.getId())) {
            throw new FotoIndisponivelException("Foto não pertence a este agendamento");
        }
        if (!galeriaQueryService.isDownloadPermitido(foto)) {
            throw new FotoIndisponivelException("Foto não está disponível para download");
        }

        eventPublisher.publishEvent(new FotoDownloadEvent(
            agendamento.getId(), List.of(fotoId)));

        return new ArquivoDownload(
            caminhoDownload(foto),
            nomeArquivoGaleria(foto));
    }

    @Transactional(readOnly = true)
    public List<FotoEnsaio> getDownloadableFotos(UUID token) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        var fotos = fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(agendamento.getId());
        return fotos.stream()
            .filter(FotoEnsaio::isVisivel)
            .filter(galeriaQueryService::isDownloadPermitido)
            .toList();
    }

    public record ZipDownload(Path zipPath, Path tempDir) implements AutoCloseable {
        @Override
        public void close() {
            limpaTempDir(tempDir);
        }
    }

    public ZipDownload downloadZip(UUID token) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        var fotos = getDownloadableFotos(token);

        if (fotos.isEmpty()) {
            throw new GaleriaNaoEncontradaException("Nenhuma foto disponível para download");
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("galeria_");
            var zipPath = tempDir.resolve("fotos_" + agendamento.getId() + ".zip");

            try (var zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
                for (var foto : fotos) {
                    var originalPath = caminhoDownload(foto);
                    if (Files.exists(originalPath)) {
                        zos.putNextEntry(new ZipEntry(nomeArquivoGaleria(foto)));
                        Files.copy(originalPath, zos);
                        zos.closeEntry();
                    }
                }
            }

            var fotoIds = fotos.stream().map(FotoEnsaio::getId).toList();
            eventPublisher.publishEvent(new FotoDownloadEvent(
                agendamento.getId(), fotoIds));

            return new ZipDownload(zipPath, tempDir);
        } catch (IOException e) {
            limpaTempDir(tempDir);
            throw new GaleriaNaoEncontradaException("Erro ao gerar arquivo ZIP: " + e.getMessage());
        }
    }

    /**
     * Nome amigavel e rastreavel para download: numero da galeria + nome original.
     * O numero garante unicidade dentro do ZIP e a identificacao usada pelo cliente.
     */
    private Path caminhoDownload(FotoEnsaio foto) {
        if (foto.getVersaoAtual() == VersaoFoto.EDITADA && foto.getEditadaPath() != null) {
            return pathResolver.resolver(foto.getEditadaPath());
        }
        return pathResolver.resolver(foto.getOriginalPath());
    }

    private static String nomeArquivoGaleria(FotoEnsaio foto) {
        var identificador = foto.getNumeroGaleria() != null
            ? String.format("%03d", foto.getNumeroGaleria())
            : String.valueOf(foto.getOrdem());
        return identificador + "_" + nomeSeguro(foto.getFileName());
    }

    private static String nomeSeguro(String nome) {
        if (nome == null || nome.isBlank()) {
            return "foto.jpg";
        }
        try {
            var apenasNome = Path.of(nome).getFileName().toString();
            return apenasNome.replaceAll("[\\r\\n\\p{Cntrl}]", "_");
        } catch (RuntimeException e) {
            return "foto.jpg";
        }
    }

    private static void limpaTempDir(Path tempDir) {
        if (tempDir != null) {
            try {
                Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
            } catch (IOException ignored) {}
        }
    }
}
