package com.photoizer.crm.foto.service;

import com.photoizer.crm.config.service.MarcaDaguaService;
import com.photoizer.crm.shared.processing.ImageProcessingService;
import com.photoizer.crm.shared.processing.QualidadePreview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.UUID;

/**
 * PATTERN: Template Method + DRY
 * Centraliza processamento de imagem (watermark + thumbnail + prévia pública)
 * com fallback e log. A prévia pública usa a qualidade configurada na galeria;
 * o thumbnail público maior é gerado sob demanda pelo {@link FotoPreviewService}.
 */
@Component
public class FotoProcessingHelper {

    private static final Logger log = LoggerFactory.getLogger(FotoProcessingHelper.class);

    private final ImageProcessingService imageProcessingService;
    private final MarcaDaguaService marcaDaguaService;

    public FotoProcessingHelper(ImageProcessingService imageProcessingService,
                                MarcaDaguaService marcaDaguaService) {
        this.imageProcessingService = imageProcessingService;
        this.marcaDaguaService = marcaDaguaService;
    }

    public record ProcessedImages(String watermarkedPath, String thumbPath, String previewPath) {}

    public ProcessedImages processar(Path original, Path targetDir, UUID fotoId,
                                     QualidadePreview qualidade) {
        var texto = marcaDaguaService.texto();
        var opacidade = marcaDaguaService.opacidade();
        String watermarkedPath = processarMarcaDagua(original, targetDir, fotoId, texto, opacidade);
        String thumbPath = processarThumb(original, targetDir, fotoId, watermarkedPath);
        String previewPath = processarPreview(original, targetDir, fotoId, texto, opacidade, qualidade);
        return new ProcessedImages(watermarkedPath, thumbPath, previewPath);
    }

    private String processarMarcaDagua(Path original, Path targetDir, UUID fotoId,
                                       String texto, float opacidade) {
        try {
            return imageProcessingService.aplicarMarcaDagua(original, targetDir, texto, opacidade)
                .toString();
        } catch (IllegalArgumentException e) {
            // Arquivo não decodificável: o chamador converte em 422 com o nome da foto.
            log.atWarn()
                .addKeyValue("event_action", "foto.imagem_invalida")
                .addKeyValue("foto_id", fotoId)
                .setCause(e)
                .log("Imagem inválida recebida no upload");
            throw e;
        } catch (Exception e) {
            log.atError()
                .addKeyValue("event_action", "foto.marca_dagua_falhou")
                .addKeyValue("foto_id", fotoId)
                .setCause(e)
                .log("Erro ao gerar marca d'água; upload abortado para não expor original");
            throw new IllegalStateException(
                "Falha ao gerar marca d'água; upload abortado para não expor a imagem original", e);
        }
    }

    private String processarThumb(Path original, Path targetDir, UUID fotoId, String fallback) {
        try {
            return imageProcessingService.gerarThumbnail(original, targetDir).toString();
        } catch (Exception e) {
            log.atWarn()
                .addKeyValue("event_action", "foto.thumbnail_falhou")
                .addKeyValue("foto_id", fotoId)
                .setCause(e)
                .log("Erro ao gerar thumbnail; usando fallback");
            return fallback;
        }
    }

    /**
     * Prévia pública protegida em resolução original. Em caso de falha,
     * retorna null para que o serviço de prévia gere a versão sob demanda a
     * partir do original (ou do watermark, como último recurso).
     */
    private String processarPreview(Path original, Path targetDir, UUID fotoId,
                                    String texto, float opacidade, QualidadePreview qualidade) {
        try {
            return imageProcessingService.aplicarMarcaDaguaPreview(
                    original, targetDir, texto, opacidade, qualidade)
                .toString();
        } catch (Exception e) {
            log.atWarn()
                .addKeyValue("event_action", "foto.preview_falhou")
                .addKeyValue("foto_id", fotoId)
                .setCause(e)
                .log("Erro ao gerar prévia protegida; será gerada sob demanda");
            return null;
        }
    }
}
