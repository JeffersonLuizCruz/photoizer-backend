package com.photoizer.crm.foto.service;

import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.VersaoFoto;
import com.photoizer.crm.shared.processing.ImageProcessingService;
import com.photoizer.crm.shared.storage.StoragePathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;

/**
 * Resolve a prévia pública protegida.
 *
 * <p>A prévia é sempre derivada do arquivo que já contém marca d'água. Fotos
 * novas usam a prévia versionada gerada no upload; fotos antigas (ou prévias de
 * uma versão anterior) são reduzidas sob demanda, preservando a marca gravada.
 */
@Service
public class FotoPreviewService {

    private static final Logger log = LoggerFactory.getLogger(FotoPreviewService.class);
    private static final String PREFIXO_PREVIEW = "preview_" + ImageProcessingService.PREVIEW_VERSION + "_";

    private final StoragePathResolver pathResolver;
    private final ImageProcessingService imageProcessingService;

    public FotoPreviewService(StoragePathResolver pathResolver,
                              ImageProcessingService imageProcessingService) {
        this.pathResolver = pathResolver;
        this.imageProcessingService = imageProcessingService;
    }

    /**
     * Retorna o caminho relativo da prévia pública na versão atual do cache.
     */
    public String caminhoPreview(FotoEnsaio foto) {
        var versao = foto.getVersaoAtual();
        var editada = versao == VersaoFoto.EDITADA;
        var salva = editada ? foto.getEditadaPreviewPath() : foto.getPreviewPath();

        if (salva != null && nomeArquivo(salva).startsWith(PREFIXO_PREVIEW)) {
            return salva;
        }

        var watermarkRelativo = editada && foto.getEditadaWatermarkedPath() != null
            ? foto.getEditadaWatermarkedPath()
            : foto.getWatermarkedPath();
        if (watermarkRelativo != null) {
            var derivada = gerarPreview(foto, watermarkRelativo, versao.name());
            if (derivada != null) {
                return derivada;
            }
        }

        // Último recurso: prévia histórica, possivelmente em tamanho/qualidade antigos.
        return salva;
    }

    private String nomeArquivo(String caminho) {
        var nome = Path.of(caminho).getFileName();
        return nome == null ? "" : nome.toString();
    }

    /**
     * Thumbnail público protegido: gerado a partir do watermark da versão
     * atual. O thumb interno do admin continua sem marca d'água.
     */
    public String caminhoThumb(FotoEnsaio foto) {
        var versao = foto.getVersaoAtual();
        var watermarkRelativo = versao == VersaoFoto.EDITADA
            && foto.getEditadaWatermarkedPath() != null
            ? foto.getEditadaWatermarkedPath()
            : foto.getWatermarkedPath();
        var fallback = versao == VersaoFoto.EDITADA && foto.getEditadaThumbPath() != null
            ? foto.getEditadaThumbPath()
            : foto.getThumbPath();

        if (watermarkRelativo == null) {
            return fallback;
        }
        var watermark = pathResolver.resolver(watermarkRelativo);
        if (watermark == null || !Files.exists(watermark)) {
            return fallback;
        }

        var target = watermark.getParent()
            .resolve("thumb_protegido_" + foto.getId() + "_" + versao.name() + ".jpg");
        if (Files.exists(target)) {
            return pathResolver.relativo(target);
        }
        try {
            imageProcessingService.gerarThumbnailProtegido(
                watermark, target,
                ImageProcessingService.THUMB_WIDTH,
                ImageProcessingService.THUMB_HEIGHT,
                0.7f);
            return pathResolver.relativo(target);
        } catch (IOException | RuntimeException e) {
            log.atWarn()
                .addKeyValue("event_action", "foto.thumb_protegido_falhou")
                .addKeyValue("foto_id", foto.getId())
                .setCause(e)
                .log("Falha ao gerar thumbnail protegido; usando thumb interno");
            return fallback;
        }
    }

    private String gerarPreview(FotoEnsaio foto, String watermarkRelativo, String versao) {
        var watermark = pathResolver.resolver(watermarkRelativo);
        if (watermark == null || !Files.exists(watermark)) {
            return null;
        }
        var target = watermark.getParent()
            .resolve(PREFIXO_PREVIEW + foto.getId() + "_" + versao + ".jpg");
        if (Files.exists(target)) {
            return pathResolver.relativo(target);
        }
        try {
            imageProcessingService.redimensionarComMarcaExistente(
                watermark, target,
                ImageProcessingService.PREVIEW_MAX_DIM,
                ImageProcessingService.PREVIEW_QUALITY);
            return pathResolver.relativo(target);
        } catch (IOException | RuntimeException e) {
            log.atWarn()
                .addKeyValue("event_action", "foto.preview_geracao_falhou")
                .addKeyValue("foto_id", foto.getId())
                .addKeyValue("versao", versao)
                .setCause(e)
                .log("Falha ao gerar prévia reduzida do watermark existente");
            return null;
        }
    }

    /**
     * Remove prévias legadas geradas sob demanda (os arquivos novos são
     * removidos pelo FotoService a partir dos próprios paths).
     */
    public void removerLegados(UUID agendamentoId, Collection<UUID> fotoIds) {
        if (fotoIds == null || fotoIds.isEmpty()) return;
        var dir = pathResolver.getUploadDir().resolve(agendamentoId.toString());
        if (!Files.isDirectory(dir)) return;

        try (var arquivos = Files.list(dir)) {
            arquivos.filter(Files::isRegularFile)
                .filter(path -> fotoIds.stream().anyMatch(id -> {
                    var nome = path.getFileName().toString();
                    return nome.startsWith(PREFIXO_PREVIEW + id + "_")
                        || nome.startsWith("preview_legacy_" + id + "_")
                        || nome.startsWith("thumb_protegido_" + id + "_");
                }))
                .forEach(this::deletar);
        } catch (IOException e) {
            log.atWarn()
                .addKeyValue("event_action", "foto.preview_limpeza_falhou")
                .addKeyValue("agendamento_id", agendamentoId)
                .setCause(e)
                .log("Falha ao limpar prévias legadas");
        }
    }

    public void removerLegados(FotoEnsaio foto) {
        removerLegados(foto.getAgendamentoId(), java.util.List.of(foto.getId()));
    }

    private void deletar(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.atWarn()
                .addKeyValue("event_action", "foto.preview_legado_delecao_falhou")
                .addKeyValue("caminho", path.toString())
                .setCause(e)
                .log("Falha ao remover prévia legada");
        }
    }
}
