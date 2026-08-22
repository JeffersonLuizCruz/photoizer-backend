package com.photoizer.crm.edicao.service;

import com.photoizer.crm.foto.service.ImageProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Template Method — centraliza processamento de imagem (watermark + thumbnail).
 * Elimina triplo try/catch duplicado que existia em publicarNoEcommerce,
 * publicarLoja e revisarFoto.
 * Facilita manutenção: alterar opacidade/tamanho em um único lugar.
 */
@Service
public class FotoEdicaoProcessor {

    private static final Logger log = LoggerFactory.getLogger(FotoEdicaoProcessor.class);
    private static final String TEXTO_MARCA_DAGUA = "© Photoizer Studio";
    private static final float OPACIDADE_MARCA = 0.15f;

    private final ImageProcessingService imageProcessingService;

    public FotoEdicaoProcessor(ImageProcessingService imageProcessingService) {
        this.imageProcessingService = imageProcessingService;
    }

    public record FotoProcessada(String watermarkedPath, String thumbPath) {}

    @FunctionalInterface
    private interface IOOperation {
        Path execute() throws IOException;
    }

    public FotoProcessada processar(Path editedPath, UUID fotoId) {
        var targetDir = editedPath.getParent();
        String wm = processarComFallback(
            () -> imageProcessingService.aplicarMarcaDagua(editedPath, targetDir, TEXTO_MARCA_DAGUA, OPACIDADE_MARCA),
            editedPath, fotoId, "marca d'água");
        String thumb = processarComFallback(
            () -> imageProcessingService.gerarThumbnail(editedPath, targetDir),
            editedPath, fotoId, "thumbnail");
        return new FotoProcessada(wm, thumb);
    }

    private String processarComFallback(IOOperation operation, Path fallback, UUID fotoId, String operacao) {
        try {
            return operation.execute().toString();
        } catch (Exception e) {
            log.warn("Erro ao gerar {} para foto {}: {}", operacao, fotoId, e.getMessage());
            return fallback.toString();
        }
    }
}
