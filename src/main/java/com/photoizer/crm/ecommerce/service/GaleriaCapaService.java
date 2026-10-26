package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.config.service.MarcaDaguaService;
import com.photoizer.crm.ecommerce.model.GaleriaEnsaio;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.VersaoFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import com.photoizer.crm.shared.processing.ImageProcessingService;
import com.photoizer.crm.shared.storage.StoragePathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Resolve e serve a capa (hero) da galeria.
 *
 * <p>A capa manual tem a versão congelada em {@code capaVersao}. Se a foto for
 * editada depois, o hero continua na versão escolhida até o staff selecionar
 * a capa novamente.
 */
@Service
@Transactional(readOnly = true)
public class GaleriaCapaService {

    private static final Logger log = LoggerFactory.getLogger(GaleriaCapaService.class);

    private final GaleriaQueryService galeriaQueryService;
    private final GaleriaEnsaioService galeriaEnsaioService;
    private final FotoEnsaioRepository fotoRepository;
    private final MarcaDaguaService marcaDaguaService;
    private final ImageProcessingService imageProcessingService;
    private final StoragePathResolver pathResolver;

    public GaleriaCapaService(GaleriaQueryService galeriaQueryService,
                              GaleriaEnsaioService galeriaEnsaioService,
                              FotoEnsaioRepository fotoRepository,
                              MarcaDaguaService marcaDaguaService,
                              ImageProcessingService imageProcessingService,
                              StoragePathResolver pathResolver) {
        this.galeriaQueryService = galeriaQueryService;
        this.galeriaEnsaioService = galeriaEnsaioService;
        this.fotoRepository = fotoRepository;
        this.marcaDaguaService = marcaDaguaService;
        this.imageProcessingService = imageProcessingService;
        this.pathResolver = pathResolver;
    }

    public record CapaResolvida(FotoEnsaio foto, VersaoFoto versao) {}

    public CapaResolvida resolver(GaleriaEnsaio galeria, List<FotoEnsaio> fotosPublicas) {
        if (fotosPublicas == null || fotosPublicas.isEmpty()) {
            return null;
        }

        var manual = fotosPublicas.stream()
            .filter(f -> f.getId().equals(galeria.getCapaFotoId()))
            .findFirst();

        var foto = manual
            .orElseGet(() -> fotosPublicas.stream()
                .filter(FotoEnsaio::isDestaque)
                .findFirst()
                .orElse(fotosPublicas.get(0)));

        // A versão congelada só vale para a capa manual. No fallback, usamos a
        // versão atual da foto para não herdar uma versão de capa já removida.
        var versao = manual.isPresent() ? galeria.getCapaVersao() : null;
        if (versao == VersaoFoto.EDITADA && foto.getEditadaPath() == null) {
            versao = VersaoFoto.ORIGINAL;
        }
        if (versao == null) {
            versao = foto.getVersaoAtual();
        }
        return new CapaResolvida(foto, versao);
    }

    public Resource servir(UUID token) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        var fotos = galeriaQueryService.listarFotosPublicadas(token);
        var galeria = galeriaEnsaioService.obter(agendamento.getId());
        var capa = resolver(galeria, fotos);
        if (capa == null) {
            return null;
        }
        var arquivo = garantirHero(capa);
        return arquivo == null ? null : new FileSystemResource(arquivo);
    }

    /**
     * Pré-gera o hero ao definir a capa, evitando espera no primeiro acesso
     * do cliente. Falhas são logadas e não impedem a gravação da capa.
     */
    public void aquecer(UUID agendamentoId, UUID fotoId) {
        try {
            var foto = fotoRepository.findById(fotoId).orElse(null);
            if (foto == null) return;
            var galeria = galeriaEnsaioService.obter(agendamentoId);
            var versao = galeria.getCapaVersao() != null
                ? galeria.getCapaVersao()
                : foto.getVersaoAtual();
            if (versao == VersaoFoto.EDITADA && foto.getEditadaPath() == null) {
                versao = VersaoFoto.ORIGINAL;
            }
            garantirHero(new CapaResolvida(foto, versao));
        } catch (RuntimeException e) {
            log.atWarn()
                .addKeyValue("event_action", "galeria.capa_aquecimento_falhou")
                .addKeyValue("agendamento_id", agendamentoId)
                .addKeyValue("foto_id", fotoId)
                .setCause(e)
                .log("Falha ao pré-gerar o hero da capa");
        }
    }

    public Path garantirHero(CapaResolvida capa) {
        var foto = capa.foto();
        var versao = capa.versao();
        var sourceRelativo = versao == VersaoFoto.EDITADA && foto.getEditadaPath() != null
            ? foto.getEditadaPath()
            : foto.getOriginalPath();
        var source = pathResolver.resolver(sourceRelativo);
        if (source == null || !Files.exists(source)) {
            return null;
        }

        var target = source.getParent().resolve(
            "hero_" + foto.getId() + "_" + versao.name() + ".jpg");
        if (Files.exists(target)) {
            return target;
        }
        try {
            imageProcessingService.aplicarMarcaDaguaEm(
                source,
                target,
                marcaDaguaService.texto(),
                marcaDaguaService.opacidade(),
                ImageProcessingService.HERO_MAX_DIM,
                ImageProcessingService.HERO_QUALITY);
            return target;
        } catch (IOException | RuntimeException e) {
            log.atWarn()
                .addKeyValue("event_action", "galeria.capa_hero_falhou")
                .addKeyValue("foto_id", foto.getId())
                .addKeyValue("versao", versao)
                .setCause(e)
                .log("Falha ao gerar o hero da capa");
            return null;
        }
    }

    public void removerHeroes(UUID agendamentoId, Collection<UUID> fotoIds) {
        if (fotoIds == null || fotoIds.isEmpty()) return;
        var dir = pathResolver.getUploadDir().resolve(agendamentoId.toString());
        if (!Files.isDirectory(dir)) return;

        try (var arquivos = Files.list(dir)) {
            arquivos.filter(Files::isRegularFile)
                .filter(path -> fotoIds.stream().anyMatch(id ->
                    path.getFileName().toString().startsWith("hero_" + id + "_")))
                .forEach(this::deletar);
        } catch (IOException e) {
            log.atWarn()
                .addKeyValue("event_action", "galeria.capa_limpeza_falhou")
                .addKeyValue("agendamento_id", agendamentoId)
                .setCause(e)
                .log("Falha ao limpar arquivos de capa");
        }
    }

    private void deletar(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.atWarn()
                .addKeyValue("event_action", "galeria.capa_delecao_falhou")
                .addKeyValue("caminho", path.toString())
                .setCause(e)
                .log("Falha ao remover arquivo de capa");
        }
    }
}
