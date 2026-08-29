package com.photoizer.crm.foto.service;

import com.photoizer.crm.shared.processing.ImageProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/**
 * PATTERN: Template Method + DRY
 * Centraliza processamento de imagem (watermark + thumbnail) com fallback e log.
 * Elimina duplicação do padrão try/catch que existia em FotoService (2 cópias)
 * e padroniza opacidade/constantes em um único lugar.
 */
@Component
public class FotoProcessingHelper {

    private static final Logger log = LoggerFactory.getLogger(FotoProcessingHelper.class);
    private static final String TEXTO_MARCA_DAGUA = "© Photoizer Studio";
    private static final float OPACIDADE_MARCA = 0.35f;

    private final ImageProcessingService imageProcessingService;

    public FotoProcessingHelper(ImageProcessingService imageProcessingService) {
        this.imageProcessingService = imageProcessingService;
    }

    public record ProcessedImages(String watermarkedPath, String thumbPath) {}

    public ProcessedImages processar(Path original, Path targetDir, UUID fotoId) {
        String watermarkedPath = processarComFallback(
            () -> imageProcessingService.aplicarMarcaDagua(original, targetDir, TEXTO_MARCA_DAGUA, OPACIDADE_MARCA),
            original, fotoId, "marca d'água");
        String thumbPath = processarComFallback(
            () -> imageProcessingService.gerarThumbnail(original, targetDir),
            original, fotoId, "thumbnail");
        return new ProcessedImages(watermarkedPath, thumbPath);
    }

    private String processarComFallback(IOOperation operation, Path fallback, UUID fotoId, String operacao) {
        try {
            return operation.execute().toString();
        } catch (Exception e) {
            log.warn("Erro ao gerar {} para foto {}: {} (usando original como fallback)",
                operacao, fotoId, e.getMessage());
            return fallback.toString();
        }
    }

    @FunctionalInterface
    private interface IOOperation {
        Path execute() throws IOException;
    }
}
