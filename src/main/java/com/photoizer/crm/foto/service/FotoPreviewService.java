package com.photoizer.crm.foto.service;

import com.photoizer.crm.config.service.MarcaDaguaService;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Resolve a prévia pública protegida.
 *
 * <p>A prévia mantém a resolução original da foto publicada pelo staff. Fotos
 * novas usam o arquivo versionado gerado no upload; fotos antigas (ou prévias
 * de uma versão anterior) são geradas sob demanda a partir do original, com a
 * marca d'água atual. Se o original não estiver disponível, o watermark
 * existente é usado como último recurso.
 */
@Service
public class FotoPreviewService {

    private static final Logger log = LoggerFactory.getLogger(FotoPreviewService.class);

    private static final String PREFIXO_PREVIEW =
        "preview_" + ImageProcessingService.PREVIEW_VERSION + "_";
    private static final String PREFIXO_THUMB_PROTEGIDO =
        "thumb_protegido_" + ImageProcessingService.PUBLIC_THUMB_VERSION + "_";
    private static final String PREFIXO_THUMB_LEGADO = "thumb_protegido_";
    private static final String PREFIXO_PREVIEW_LEGADO = "preview_legacy_";

    private final StoragePathResolver pathResolver;
    private final ImageProcessingService imageProcessingService;
    private final MarcaDaguaService marcaDaguaService;

    public FotoPreviewService(StoragePathResolver pathResolver,
                              ImageProcessingService imageProcessingService,
                              MarcaDaguaService marcaDaguaService) {
        this.pathResolver = pathResolver;
        this.imageProcessingService = imageProcessingService;
        this.marcaDaguaService = marcaDaguaService;
    }

    /**
     * Retorna o caminho relativo da prévia pública na versão atual do cache.
     */
    public String caminhoPreview(FotoEnsaio foto) {
        var versao = foto.getVersaoAtual();
        var editada = versao == VersaoFoto.EDITADA;
        var salva = editada ? foto.getEditadaPreviewPath() : foto.getPreviewPath();

        if (ehCacheAtual(salva)) {
            return salva;
        }

        var fonte = fontePreview(foto, editada);
        if (fonte != null) {
            var derivada = gerarPreviewDaFonte(foto, fonte, versao.name());
            if (derivada != null) {
                return derivada;
            }
        }

        var watermarkRelativo = watermarkRelativo(foto, editada);
        if (watermarkRelativo != null) {
            var derivada = gerarPreviewDoWatermark(foto, watermarkRelativo, versao.name());
            if (derivada != null) {
                return derivada;
            }
        }

        // Último recurso: prévia histórica, possivelmente em tamanho/qualidade antigos.
        return salva;
    }

    /**
     * Garante a prévia da versão atual a partir do original/editada. Usado pelo
     * pré-aquecimento administrativo; retorna vazio quando o original não está
     * disponível ou o processamento falha.
     */
    public Optional<String> regenerarPreview(FotoEnsaio foto) {
        var versao = foto.getVersaoAtual();
        var editada = versao == VersaoFoto.EDITADA;
        var salva = editada ? foto.getEditadaPreviewPath() : foto.getPreviewPath();
        if (ehCacheAtual(salva)) {
            return Optional.of(salva);
        }
        var fonte = fontePreview(foto, editada);
        if (fonte == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(gerarPreviewDaFonte(foto, fonte, versao.name()));
    }

    /**
     * Thumbnail público protegido: gerado a partir do watermark da versão
     * atual. O thumb interno do admin continua sem marca d'água.
     */
    public String caminhoThumb(FotoEnsaio foto) {
        var versao = foto.getVersaoAtual();
        var watermarkRelativo = watermarkRelativo(foto, versao == VersaoFoto.EDITADA);
        var fallback = versao == VersaoFoto.EDITADA && foto.getEditadaThumbPath() != null
            ? foto.getEditadaThumbPath()
            : foto.getThumbPath();

        if (watermarkRelativo == null) {
            return fallback;
        }
        var watermark = pathResolver.resolver(watermarkRelativo);
        if (watermark == null) {
            return fallback;
        }

        var target = watermark.getParent()
            .resolve(PREFIXO_THUMB_PROTEGIDO + foto.getId() + "_" + versao.name() + ".jpg");
        if (Files.exists(target)) {
            return pathResolver.relativo(target);
        }
        if (!Files.exists(watermark)) {
            return fallback;
        }
        try {
            imageProcessingService.gerarThumbnailProtegido(
                watermark, target,
                ImageProcessingService.PUBLIC_THUMB_WIDTH,
                ImageProcessingService.PUBLIC_THUMB_HEIGHT,
                ImageProcessingService.PUBLIC_THUMB_QUALITY);
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

    /**
     * Remove o cache de prévia de uma versão anterior depois que o caminho novo
     * foi gravado no banco. Não toca no caminho atual nem em arquivos fora do
     * padrão versionado.
     */
    public void removerPreviewAntiga(String caminho) {
        if (caminho == null || caminho.isBlank()) {
            return;
        }
        var nome = nomeArquivo(caminho);
        if (nome.startsWith(PREFIXO_PREVIEW) || !nome.startsWith("preview_v")) {
            return;
        }
        deletar(pathResolver.resolver(caminho));
    }

    private String fontePreview(FotoEnsaio foto, boolean editada) {
        if (editada && foto.getEditadaPath() != null) {
            return foto.getEditadaPath();
        }
        return foto.getOriginalPath();
    }

    private String watermarkRelativo(FotoEnsaio foto, boolean editada) {
        return editada && foto.getEditadaWatermarkedPath() != null
            ? foto.getEditadaWatermarkedPath()
            : foto.getWatermarkedPath();
    }

    private boolean ehCacheAtual(String caminho) {
        return caminho != null
            && nomeArquivo(caminho).startsWith(PREFIXO_PREVIEW);
    }

    private String nomeArquivo(String caminho) {
        var nome = Path.of(caminho).getFileName();
        return nome == null ? "" : nome.toString();
    }

    private String gerarPreviewDaFonte(FotoEnsaio foto, String fonteRelativa, String versao) {
        var fonte = pathResolver.resolver(fonteRelativa);
        if (fonte == null) {
            return null;
        }
        var target = fonte.getParent()
            .resolve(PREFIXO_PREVIEW + foto.getId() + "_" + versao + ".jpg");
        // O cache vale mesmo que o original tenha sido movido/removido depois.
        if (Files.exists(target)) {
            return pathResolver.relativo(target);
        }
        if (!Files.exists(fonte)) {
            return null;
        }
        try {
            imageProcessingService.aplicarMarcaDaguaEm(
                fonte, target,
                marcaDaguaService.texto(),
                marcaDaguaService.opacidade(),
                ImageProcessingService.PREVIEW_MAX_DIM,
                ImageProcessingService.PREVIEW_QUALITY);
            return pathResolver.relativo(target);
        } catch (IOException | RuntimeException e) {
            log.atWarn()
                .addKeyValue("event_action", "foto.preview_geracao_falhou")
                .addKeyValue("foto_id", foto.getId())
                .addKeyValue("versao", versao)
                .addKeyValue("fonte", fonteRelativa)
                .setCause(e)
                .log("Falha ao gerar prévia em alta resolução a partir do original");
            return null;
        }
    }

    /**
     * Fallback para fotos antigas cujo original não está mais disponível.
     * Reaproveita a marca já gravada, limitada à resolução do watermark.
     */
    private String gerarPreviewDoWatermark(FotoEnsaio foto, String watermarkRelativo, String versao) {
        var watermark = pathResolver.resolver(watermarkRelativo);
        if (watermark == null) {
            return null;
        }
        var target = watermark.getParent()
            .resolve(PREFIXO_PREVIEW + foto.getId() + "_" + versao + ".jpg");
        if (Files.exists(target)) {
            return pathResolver.relativo(target);
        }
        if (!Files.exists(watermark)) {
            return null;
        }
        try {
            imageProcessingService.redimensionarComMarcaExistente(
                watermark, target,
                ImageProcessingService.WATERMARK_MAX_DIM,
                ImageProcessingService.PREVIEW_QUALITY);
            return pathResolver.relativo(target);
        } catch (IOException | RuntimeException e) {
            log.atWarn()
                .addKeyValue("event_action", "foto.preview_fallback_falhou")
                .addKeyValue("foto_id", foto.getId())
                .addKeyValue("versao", versao)
                .setCause(e)
                .log("Falha ao gerar prévia a partir do watermark existente");
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
        var diretorios = java.util.List.of(dir, dir.resolve("orig"), dir.resolve("editadas"));

        for (var diretorio : diretorios) {
            if (!Files.isDirectory(diretorio)) {
                continue;
            }
            try (var arquivos = Files.list(diretorio)) {
                arquivos.filter(Files::isRegularFile)
                    .filter(path -> fotoIds.stream().anyMatch(id -> {
                        var nome = path.getFileName().toString();
                        return nome.startsWith(PREFIXO_PREVIEW + id + "_")
                            || nome.startsWith(PREFIXO_PREVIEW_LEGADO + id + "_")
                            || nome.startsWith(PREFIXO_THUMB_PROTEGIDO + id + "_")
                            || nome.startsWith(PREFIXO_THUMB_LEGADO + id + "_");
                    }))
                    .forEach(this::deletar);
            } catch (IOException e) {
                log.atWarn()
                    .addKeyValue("event_action", "foto.preview_limpeza_falhou")
                    .addKeyValue("agendamento_id", agendamentoId)
                    .addKeyValue("diretorio", diretorio.toString())
                    .setCause(e)
                    .log("Falha ao limpar prévias legadas");
            }
        }
    }

    public void removerLegados(FotoEnsaio foto) {
        removerLegados(foto.getAgendamentoId(), java.util.List.of(foto.getId()));
    }

    private void deletar(Path path) {
        if (path == null) {
            return;
        }
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
