package com.photoizer.crm.foto.service;

import com.photoizer.crm.shared.processing.ImageProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
        String watermarkedPath = processarMarcaDagua(original, targetDir, fotoId);
        String thumbPath = processarThumb(original, targetDir, fotoId, watermarkedPath);
        return new ProcessedImages(watermarkedPath, thumbPath);
    }

    private String processarMarcaDagua(Path original, Path targetDir, UUID fotoId) {
        try {
            return imageProcessingService.aplicarMarcaDagua(original, targetDir, TEXTO_MARCA_DAGUA, OPACIDADE_MARCA)
                .toString();
        } catch (Exception e) {
            log.error("Erro ao gerar marca d'água para foto {}: {} (upload abortado para não expor original)",
                fotoId, e.getMessage());
            throw new IllegalStateException(
                "Falha ao gerar marca d'água; upload abortado para não expor a imagem original", e);
        }
    }

    private String processarThumb(Path original, Path targetDir, UUID fotoId, String fallback) {
        try {
            return imageProcessingService.gerarThumbnail(original, targetDir).toString();
        } catch (Exception e) {
            log.warn("Erro ao gerar thumbnail para foto {}: {} (usando fallback)",
                fotoId, e.getMessage());
            return fallback;
        }
    }
}
