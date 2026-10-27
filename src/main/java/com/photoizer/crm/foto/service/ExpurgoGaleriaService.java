package com.photoizer.crm.foto.service;

import com.photoizer.crm.foto.acl.AgendamentoArmazenamentoReadService;
import com.photoizer.crm.foto.api.ExpurgarGaleriaResponse;
import com.photoizer.crm.foto.event.GaleriaArquivadaEvent;
import com.photoizer.crm.foto.exception.ExpurgoNaoPermitidoException;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.security.CurrentUser;
import com.photoizer.crm.shared.storage.StoragePathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Expurgo dos arquivos de uma galeria finalizada.
 *
 * <p>Apaga original, marca d'água, thumbnail, prévia, versão editada e os
 * caches derivados ({@code preview_v3_*}, {@code thumb_protegido_*},
 * {@code hero_*}), preservando <b>todos</b> os registros de {@link FotoEnsaio}.
 * Comprovantes de pagamento e contratos assinados ficam intocados: a remoção é
 * feita por lista explícita de caminhos das fotos, nunca pela pasta inteira.
 *
 * <p>O estado é derivado das flags {@code arquivosRemovidos} de cada foto, de
 * modo que uma execução interrompida pode ser retomada sem efeitos colaterais.
 */
@Service
@Transactional
public class ExpurgoGaleriaService {

    private static final Logger log = LoggerFactory.getLogger(ExpurgoGaleriaService.class);

    private static final Set<StatusFoto> STATUS_PENDENTES = EnumSet.of(
        StatusFoto.AGUARDANDO_COMPROVANTE,
        StatusFoto.AGUARDANDO_CONFIRMACAO);

    /**
     * Caches derivados carregam o id da foto no nome:
     * {@code preview_v3_<fotoId>_<versão>.jpg}, {@code thumb_protegido_<fotoId>_<versão>.jpg},
     * {@code hero_<fotoId>_<versão>.jpg} e {@code preview_legacy_<fotoId>_*}.
     */
    private static final Pattern CACHE_DA_FOTO = Pattern.compile(
        "^(preview_v3_|thumb_protegido_|hero_|preview_legacy_)([0-9a-fA-F-]{36})_.*");

    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final AgendamentoArmazenamentoReadService agendamentoReadService;
    private final StoragePathResolver pathResolver;
    private final ArmazenamentoService armazenamentoService;
    private final ApplicationEventPublisher eventPublisher;

    public ExpurgoGaleriaService(FotoEnsaioRepository fotoEnsaioRepository,
                                 AgendamentoArmazenamentoReadService agendamentoReadService,
                                 StoragePathResolver pathResolver,
                                 ArmazenamentoService armazenamentoService,
                                 ApplicationEventPublisher eventPublisher) {
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.agendamentoReadService = agendamentoReadService;
        this.pathResolver = pathResolver;
        this.armazenamentoService = armazenamentoService;
        this.eventPublisher = eventPublisher;
    }

    public ExpurgarGaleriaResponse expurgar(UUID agendamentoId) {
        if (!agendamentoReadService.isFinalizado(agendamentoId)) {
            throw new ExpurgoNaoPermitidoException(ErrorCode.ENSAIO_NAO_FINALIZADO,
                "Finalize o ensaio antes de liberar o espaço da galeria.");
        }

        var todas = fotoEnsaioRepository.findAllByAgendamentoIdForUpdate(agendamentoId);
        if (todas.isEmpty()) {
            throw new ExpurgoNaoPermitidoException("A galeria não possui fotos cadastradas.");
        }

        var pendentes = todas.stream()
            .filter(foto -> !foto.isArquivosRemovidos())
            .toList();
        if (pendentes.isEmpty()) {
            throw new ExpurgoNaoPermitidoException("A galeria já foi arquivada.");
        }

        var comPendencia = pendentes.stream().filter(this::temPendencia).count();
        if (comPendencia > 0) {
            throw new ExpurgoNaoPermitidoException(
                comPendencia + " foto(s) com seleção ativa ou compra pendente.");
        }

        var diretorio = pathResolver.getUploadDir().resolve(agendamentoId.toString());
        var bytesAntes = tamanhoDiretorio(diretorio);
        var cachesPorFoto = mapearCaches(diretorio);

        var agora = LocalDateTime.now();
        var usuario = CurrentUser.name();
        long bytesFotos = 0;

        for (var foto : pendentes) {
            var liberado = apagarArquivos(foto, cachesPorFoto.getOrDefault(foto.getId(), List.of()));
            bytesFotos += liberado;
            foto.setArquivosRemovidos(true);
            foto.setArquivosRemovidosEm(agora);
            foto.setArquivosRemovidosPor(usuario);
            foto.setBytesLiberados(liberado);
        }
        fotoEnsaioRepository.saveAll(pendentes);

        // Limpa carrinho, favoritos, comentários, capa/hero e caches no ecommerce.
        eventPublisher.publishEvent(new GaleriaArquivadaEvent(
            agendamentoId, pendentes.stream().map(FotoEnsaio::getId).toList()));

        var liberadoTotal = Math.max(0, bytesAntes - tamanhoDiretorio(diretorio));
        armazenamentoService.invalidarCache();
        log.atInfo()
            .addKeyValue("event_action", "galeria.expurgada")
            .addKeyValue("agendamento_id", agendamentoId)
            .addKeyValue("fotos", pendentes.size())
            .addKeyValue("bytes_fotos", bytesFotos)
            .addKeyValue("bytes_liberados", liberadoTotal)
            .addKeyValue("usuario", usuario)
            .log("Arquivos da galeria expurgados");

        return new ExpurgarGaleriaResponse(pendentes.size(), liberadoTotal);
    }

    private boolean temPendencia(FotoEnsaio foto) {
        return foto.isEmSelecaoEdicao() || STATUS_PENDENTES.contains(foto.getStatus());
    }

    private List<String> caminhos(FotoEnsaio foto) {
        var caminhos = new ArrayList<String>(8);
        caminhos.add(foto.getOriginalPath());
        caminhos.add(foto.getWatermarkedPath());
        caminhos.add(foto.getThumbPath());
        caminhos.add(foto.getPreviewPath());
        caminhos.add(foto.getEditadaPath());
        caminhos.add(foto.getEditadaWatermarkedPath());
        caminhos.add(foto.getEditadaThumbPath());
        caminhos.add(foto.getEditadaPreviewPath());
        return caminhos;
    }

    private long apagarArquivos(FotoEnsaio foto, List<Path> caches) {
        long total = 0;
        for (var caminho : caminhos(foto)) {
            total += apagar(pathResolver.resolver(caminho));
        }
        for (var cache : caches) {
            total += apagar(cache);
        }
        return total;
    }

    private long apagar(Path caminho) {
        if (caminho == null) {
            return 0;
        }
        try {
            if (!Files.isRegularFile(caminho)) {
                return 0;
            }
            var bytes = Files.size(caminho);
            Files.delete(caminho);
            return bytes;
        } catch (IOException e) {
            log.atWarn()
                .addKeyValue("event_action", "galeria.expurgo_arquivo_falhou")
                .addKeyValue("caminho", caminho.toString())
                .setCause(e)
                .log("Falha ao apagar arquivo no expurgo da galeria");
            return 0;
        }
    }

    /**
     * Varre a pasta da galeria uma única vez e agrupa os caches pelo id da
     * foto. Evita uma varredura por foto em galerias grandes.
     */
    private Map<UUID, List<Path>> mapearCaches(Path diretorio) {
        var caches = new HashMap<UUID, List<Path>>();
        if (!Files.isDirectory(diretorio)) {
            return caches;
        }

        List<Path> subdiretorios = List.of(diretorio, diretorio.resolve("orig"), diretorio.resolve("editadas"));
        for (var subdiretorio : subdiretorios) {
            if (!Files.isDirectory(subdiretorio)) {
                continue;
            }
            try (var arquivos = Files.list(subdiretorio)) {
                arquivos.filter(Files::isRegularFile).forEach(arquivo -> {
                    var matcher = CACHE_DA_FOTO.matcher(arquivo.getFileName().toString());
                    if (!matcher.matches()) {
                        return;
                    }
                    var fotoId = UUID.fromString(matcher.group(2));
                    caches.computeIfAbsent(fotoId, id -> new ArrayList<>()).add(arquivo);
                });
            } catch (IOException | UncheckedIOException e) {
                log.atWarn()
                    .addKeyValue("event_action", "galeria.expurgo_cache_leitura_falhou")
                    .addKeyValue("diretorio", subdiretorio.toString())
                    .setCause(e)
                    .log("Falha ao listar caches de prévia da galeria");
            }
        }
        return caches;
    }

    private long tamanhoDiretorio(Path diretorio) {
        if (!Files.isDirectory(diretorio)) {
            return 0;
        }
        try (Stream<Path> arquivos = Files.walk(diretorio)) {
            return arquivos.filter(Files::isRegularFile)
                .mapToLong(this::tamanho)
                .sum();
        } catch (IOException | UncheckedIOException e) {
            log.atWarn()
                .addKeyValue("event_action", "galeria.expurgo_medicao_falhou")
                .addKeyValue("diretorio", diretorio.toString())
                .setCause(e)
                .log("Falha ao medir o diretório da galeria");
            return 0;
        }
    }

    private long tamanho(Path caminho) {
        try {
            return Files.size(caminho);
        } catch (IOException e) {
            return 0;
        }
    }
}
