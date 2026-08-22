package com.photoizer.crm.edicao.service;

import com.photoizer.crm.edicao.exception.EdicaoNaoEncontradaException;
import com.photoizer.crm.edicao.repository.EdicaoRepository;
import com.photoizer.crm.edicao.repository.FotoEdicaoRepository;
import com.photoizer.crm.shared.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Serviço de geração de ZIPs para download de fotos RAW/editadas.
 * Extraído de EdicaoService. Limpeza melhorada: remove ZIPs com mais de 1h.
 */
@Service
public class EdicaoZipService {

    private static final Logger log = LoggerFactory.getLogger(EdicaoZipService.class);
    private static final long ZIP_MAX_AGE_HOURS = 1;

    private final EdicaoRepository edicaoRepository;
    private final FotoEdicaoRepository fotoEdicaoRepository;
    private final FileStorageService fileStorageService;

    public EdicaoZipService(EdicaoRepository edicaoRepository,
                            FotoEdicaoRepository fotoEdicaoRepository,
                            FileStorageService fileStorageService) {
        this.edicaoRepository = edicaoRepository;
        this.fotoEdicaoRepository = fotoEdicaoRepository;
        this.fileStorageService = fileStorageService;
    }

    public Path gerarZipRaw(UUID agendamentoId) throws IOException {
        var edicao = edicaoRepository.findByAgendamentoId(agendamentoId)
            .orElseThrow(() -> new EdicaoNaoEncontradaException("Processo de edição não encontrado"));

        var fotos = fotoEdicaoRepository.findByEdicaoIdOrderByOrdemAsc(edicao.getId());

        var zipDir = fileStorageService.getUploadDir().resolve("temp");
        Files.createDirectories(zipDir);
        limparZipsAntigos(zipDir);
        var zipPath = zipDir.resolve("raw_" + agendamentoId + "_" + UUID.randomUUID() + ".zip");

        try (var zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            for (var foto : fotos) {
                var filePath = Path.of(foto.getRawPath());
                if (Files.exists(filePath)) {
                    zos.putNextEntry(new ZipEntry(foto.getRawFileName()));
                    Files.copy(filePath, zos);
                    zos.closeEntry();
                }
            }
        }

        return zipPath;
    }

    public Path gerarZipEditadas(UUID agendamentoId) throws IOException {
        var edicao = edicaoRepository.findByAgendamentoId(agendamentoId)
            .orElseThrow(() -> new EdicaoNaoEncontradaException("Processo de edição não encontrado"));

        var fotos = fotoEdicaoRepository.findByEdicaoIdOrderByOrdemAsc(edicao.getId());

        var zipDir = fileStorageService.getUploadDir().resolve("temp");
        Files.createDirectories(zipDir);
        limparZipsAntigos(zipDir);
        var zipPath = zipDir.resolve("editadas_" + agendamentoId + "_" + UUID.randomUUID() + ".zip");

        try (var zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            for (var foto : fotos) {
                var caminho = foto.getEditedPath() != null ? foto.getEditedPath() : foto.getRawPath();
                var nome = foto.getEditedFileName() != null ? foto.getEditedFileName() : foto.getRawFileName();
                var filePath = Path.of(caminho);
                if (Files.exists(filePath)) {
                    zos.putNextEntry(new ZipEntry(nome));
                    Files.copy(filePath, zos);
                    zos.closeEntry();
                }
            }
        }

        return zipPath;
    }

    private void limparZipsAntigos(Path diretorio) {
        if (!Files.exists(diretorio)) return;
        try (var files = Files.list(diretorio)) {
            var cutoff = Instant.now().minus(ZIP_MAX_AGE_HOURS, ChronoUnit.HOURS);
            files.filter(f -> {
                try {
                    return Files.getLastModifiedTime(f).toInstant().isBefore(cutoff);
                } catch (IOException e) {
                    return false;
                }
            }).forEach(f -> {
                try { Files.deleteIfExists(f); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }
}
