package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.event.FotoDownloadEvent;
import com.photoizer.crm.ecommerce.exception.FotoIndisponivelException;
import com.photoizer.crm.ecommerce.exception.FotoNaoEncontradaException;
import com.photoizer.crm.ecommerce.exception.GaleriaNaoEncontradaException;
import com.photoizer.crm.foto.model.FotoEnsaio;
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

    public DownloadService(FotoEnsaioRepository fotoEnsaioRepository,
                           GaleriaQueryService galeriaQueryService,
                           ApplicationEventPublisher eventPublisher) {
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.galeriaQueryService = galeriaQueryService;
        this.eventPublisher = eventPublisher;
    }

    public Path downloadFoto(UUID token, UUID fotoId) {
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

        return Path.of(foto.getOriginalPath());
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

    public Path downloadZip(UUID token) {
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
                    var originalPath = Path.of(foto.getOriginalPath());
                    if (Files.exists(originalPath)) {
                        var entryName = foto.getOrdem() + "_" + foto.getFileName();
                        zos.putNextEntry(new ZipEntry(entryName));
                        Files.copy(originalPath, zos);
                        zos.closeEntry();
                    }
                }
            }

            var fotoIds = fotos.stream().map(FotoEnsaio::getId).toList();
            eventPublisher.publishEvent(new FotoDownloadEvent(
                agendamento.getId(), fotoIds));

            return zipPath;
        } catch (IOException e) {
            limpaTempDir(tempDir);
            throw new GaleriaNaoEncontradaException("Erro ao gerar arquivo ZIP: " + e.getMessage());
        }
    }

    private void limpaTempDir(Path tempDir) {
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
