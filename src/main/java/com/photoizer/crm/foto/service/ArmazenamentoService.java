package com.photoizer.crm.foto.service;

import com.photoizer.crm.foto.acl.AgendamentoArmazenamentoReadService;
import com.photoizer.crm.foto.acl.AgendamentoResumo;
import com.photoizer.crm.foto.api.ArmazenamentoResumoResponse;
import com.photoizer.crm.foto.api.GaleriaArmazenamentoResponse;
import com.photoizer.crm.foto.model.MotivoExpurgoBloqueado;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;
import com.photoizer.crm.shared.storage.StoragePathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mede o consumo de disco do diretório de uploads e cruza com as galerias.
 *
 * <p>As fotos ficam em {@code uploads/{agendamentoId}/}; o banco guarda apenas
 * os caminhos. A varredura é feita <b>uma vez</b> e cacheada por alguns
 * segundos, porque a página de armazenamento chama mais de um endpoint.
 *
 * <p>A classificação por tipo usa os prefixos gerados pelo pipeline de imagem:
 * {@code orig_} (arquivo original), {@code wm_}, {@code thumb_},
 * {@code preview_} e {@code hero_} (derivados), {@code editadas/} (versões
 * editadas) e {@code comprovante_*} (documentos que o expurgo preserva).
 */
@Service
@Transactional(readOnly = true)
public class ArmazenamentoService {

    private static final Logger log = LoggerFactory.getLogger(ArmazenamentoService.class);

    private static final Set<StatusFoto> STATUS_PENDENTES = EnumSet.of(
        StatusFoto.AGUARDANDO_COMPROVANTE,
        StatusFoto.AGUARDANDO_CONFIRMACAO);

    private final StoragePathResolver pathResolver;
    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final AgendamentoArmazenamentoReadService agendamentoReadService;
    private final long cacheMillis;

    private volatile CacheUso cacheUso;

    public ArmazenamentoService(StoragePathResolver pathResolver,
                                FotoEnsaioRepository fotoEnsaioRepository,
                                AgendamentoArmazenamentoReadService agendamentoReadService,
                                @Value("${app.storage.cache-seconds:60}") long cacheSeconds) {
        this.pathResolver = pathResolver;
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.agendamentoReadService = agendamentoReadService;
        this.cacheMillis = Math.max(0, cacheSeconds) * 1000;
    }

    public ArmazenamentoResumoResponse resumo() {
        var contexto = carregarContexto();
        var uso = contexto.uso();
        var totalFotos = contexto.contagens().values().stream()
            .mapToLong(Contagem::total)
            .sum();
        var galeriasArquivadas = (int) contexto.contagens().values().stream()
            .filter(contagem -> contagem.total() > 0 && contagem.arquivadas() == contagem.total())
            .count();
        var bytesLiberados = contexto.arquivamentos().values().stream()
            .mapToLong(Arquivamento::bytes)
            .sum();

        return new ArmazenamentoResumoResponse(
            uso.fotos(),
            uso.derivados(),
            uso.editadas(),
            uso.documentos(),
            uso.propostas(),
            uso.outros(),
            uso.total(),
            contexto.volume().total(),
            contexto.volume().livre(),
            percentualUso(contexto.volume()),
            contexto.contagens().size(),
            totalFotos,
            galeriasArquivadas,
            bytesLiberados
        );
    }

    public List<GaleriaArmazenamentoResponse> galerias() {
        var contexto = carregarContexto();
        return contexto.resumos().stream()
            .map(resumo -> montar(resumo, contexto))
            .filter(galeria -> galeria.quantidadeFotos() > 0 || galeria.bytes() > 0)
            .sorted(Comparator
                .comparingLong(GaleriaArmazenamentoResponse::bytes)
                .reversed()
                .thenComparing(GaleriaArmazenamentoResponse::dataHoraEnsaio,
                    Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
    }

    public GaleriaArmazenamentoResponse galeria(UUID agendamentoId) {
        var resumo = agendamentoReadService.buscar(agendamentoId)
            .orElseThrow(() -> new NotFoundException(
                ErrorCode.AGENDAMENTO_NAO_ENCONTRADO, "ID: " + agendamentoId));
        return montar(resumo, carregarContexto());
    }

    /**
     * Manifesto CSV com o que será apagado, para o staff guardar antes do
     * expurgo (a operação é irreversível).
     */
    public byte[] manifesto(UUID agendamentoId) {
        agendamentoReadService.buscar(agendamentoId)
            .orElseThrow(() -> new NotFoundException(
                ErrorCode.AGENDAMENTO_NAO_ENCONTRADO, "ID: " + agendamentoId));

        var csv = new StringBuilder("\ufeffnumero_galeria;foto_id;nome_arquivo;status;arquivada;bytes_liberados\n");
        for (var foto : fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId)) {
            csv.append(csvValor(foto.getNumeroGaleria() == null
                    ? String.valueOf(foto.getOrdem())
                    : String.valueOf(foto.getNumeroGaleria())))
                .append(';')
                .append(foto.getId())
                .append(';')
                .append(csvValor(foto.getFileName()))
                .append(';')
                .append(foto.getStatus())
                .append(';')
                .append(foto.isArquivosRemovidos())
                .append(';')
                .append(foto.getBytesLiberados() == null ? 0 : foto.getBytesLiberados())
                .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvValor(String valor) {
        if (valor == null) {
            return "";
        }
        var limpo = valor.replace("\"", "\"\"");
        return limpo.contains(";") || limpo.contains("\"") || limpo.contains("\n")
            ? "\"" + limpo + "\""
            : limpo;
    }

    private GaleriaArmazenamentoResponse montar(AgendamentoResumo resumo, Contexto contexto) {
        var id = resumo.id();
        var contagem = contexto.contagens().getOrDefault(id, new Contagem(0, 0));
        var uso = contexto.uso().porGaleria().getOrDefault(id, UsoGaleria.VAZIA);
        var arquivamento = contexto.arquivamentos().get(id);
        var arquivada = contagem.total() > 0 && contagem.arquivadas() == contagem.total();
        var motivo = motivoBloqueio(resumo, contagem, arquivada,
            contexto.pendencias().getOrDefault(id, 0L));

        return new GaleriaArmazenamentoResponse(
            id,
            resumo.clienteNome(),
            resumo.dataHoraEnsaio(),
            resumo.status(),
            (int) contagem.total(),
            (int) contagem.arquivadas(),
            uso.total(),
            uso.fotos(),
            uso.documentos(),
            contexto.ultimosDownloads().get(id),
            arquivada,
            arquivamento != null ? arquivamento.em() : null,
            arquivamento != null ? arquivamento.bytes() : 0,
            motivo == null,
            motivo
        );
    }

    private MotivoExpurgoBloqueado motivoBloqueio(AgendamentoResumo resumo,
                                                  Contagem contagem,
                                                  boolean arquivada,
                                                  long pendencias) {
        if (!resumo.finalizado()) {
            return MotivoExpurgoBloqueado.NAO_FINALIZADO;
        }
        if (arquivada) {
            return MotivoExpurgoBloqueado.JA_ARQUIVADA;
        }
        if (contagem.total() == 0) {
            return MotivoExpurgoBloqueado.SEM_FOTOS;
        }
        if (pendencias > 0) {
            return MotivoExpurgoBloqueado.PENDENCIA;
        }
        return null;
    }

    private Contexto carregarContexto() {
        var resumos = agendamentoReadService.listar();
        var contagens = fotoEnsaioRepository.contarPorAgendamento().stream()
            .collect(Collectors.toMap(
                FotoEnsaioRepository.ContagemFotos::getAgendamentoId,
                contagem -> new Contagem(contagem.getTotal(), contagem.getArquivadas())));
        var ultimosDownloads = fotoEnsaioRepository.ultimoDownloadPorAgendamento().stream()
            .filter(download -> download.getUltimoDownload() != null)
            .collect(Collectors.toMap(
                FotoEnsaioRepository.UltimoDownload::getAgendamentoId,
                FotoEnsaioRepository.UltimoDownload::getUltimoDownload));
        var arquivamentos = fotoEnsaioRepository.arquivamentoPorAgendamento().stream()
            .collect(Collectors.toMap(
                FotoEnsaioRepository.Arquivamento::getAgendamentoId,
                arquivamento -> new Arquivamento(
                    arquivamento.getArquivadaEm(), arquivamento.getBytesLiberados())));
        var pendencias = fotoEnsaioRepository.pendenciasPorAgendamento(STATUS_PENDENTES).stream()
            .collect(Collectors.toMap(
                FotoEnsaioRepository.PendenciaFotos::getAgendamentoId,
                FotoEnsaioRepository.PendenciaFotos::getPendencias));

        var uploadDir = pathResolver.getUploadDir();
        return new Contexto(
            resumos,
            contagens,
            ultimosDownloads,
            arquivamentos,
            pendencias,
            usoDisco(uploadDir),
            volume(uploadDir)
        );
    }

    private UsoDisco usoDisco(Path uploadDir) {
        var atual = cacheUso;
        var agora = System.currentTimeMillis();
        if (atual != null && atual.expiraEm() > agora) {
            return atual.uso();
        }

        var inicio = System.currentTimeMillis();
        var uso = varrerDisco(uploadDir);
        if (cacheMillis > 0) {
            cacheUso = new CacheUso(uso, agora + cacheMillis);
        }
        log.atDebug()
            .addKeyValue("event_action", "armazenamento.varredura_disco")
            .addKeyValue("bytes_total", uso.total())
            .addKeyValue("galerias", uso.porGaleria().size())
            .addKeyValue("duracao_ms", System.currentTimeMillis() - inicio)
            .log("Diretório de uploads medido");
        return uso;
    }

    private UsoDisco varrerDisco(Path uploadDir) {
        var porGaleria = new HashMap<UUID, UsoGaleria>();
        long fotos = 0;
        long derivados = 0;
        long editadas = 0;
        long documentos = 0;
        long propostas = 0;
        long outros = 0;

        if (!Files.isDirectory(uploadDir)) {
            return new UsoDisco(0, 0, 0, 0, 0, 0, 0, Map.of());
        }

        try (var arquivos = Files.walk(uploadDir)) {
            for (var caminho : (Iterable<Path>) arquivos.filter(Files::isRegularFile)::iterator) {
                var bytes = tamanho(caminho);
                if (bytes <= 0) {
                    continue;
                }
                var relativo = uploadDir.relativize(caminho);
                var nome = relativo.getFileName().toString();

                if (ehProposta(relativo)) {
                    propostas += bytes;
                    continue;
                }

                var agendamentoId = agendamentoIdDoPrimeiroNivel(relativo);
                if (agendamentoId == null) {
                    outros += bytes;
                    continue;
                }

                var categoria = categoria(relativo, nome);
                var uso = porGaleria.computeIfAbsent(agendamentoId, id -> new UsoGaleria());
                uso.somar(categoria, bytes);

                switch (categoria) {
                    case FOTOS -> fotos += bytes;
                    case DERIVADOS -> derivados += bytes;
                    case EDITADAS -> editadas += bytes;
                    case DOCUMENTOS -> documentos += bytes;
                    case OUTROS -> outros += bytes;
                }
            }
        } catch (IOException | UncheckedIOException e) {
            log.atWarn()
                .addKeyValue("event_action", "armazenamento.varredura_falhou")
                .addKeyValue("upload_dir", uploadDir.toString())
                .setCause(e)
                .log("Falha ao medir o diretório de uploads; usando o que já foi lido");
        }

        return new UsoDisco(
            fotos, derivados, editadas, documentos, propostas, outros,
            fotos + derivados + editadas + documentos + propostas + outros,
            Map.copyOf(porGaleria));
    }

    /**
     * Derivados de uma foto vivem em {@code orig/} junto do original; o nome do
     * arquivo revela a categoria. Arquivos desconhecidos vão para "outros" em
     * vez de inflar o total de fotos.
     */
    private Categoria categoria(Path relativo, String nome) {
        if (relativo.getNameCount() >= 3) {
            var subdiretorio = relativo.getName(1).toString();
            if ("editadas".equals(subdiretorio)) {
                return Categoria.EDITADAS;
            }
            if ("orig".equals(subdiretorio)) {
                return nome.startsWith("orig_") ? Categoria.FOTOS : Categoria.DERIVADOS;
            }
        }
        if (nome.startsWith("comprovante_")) {
            return Categoria.DOCUMENTOS;
        }
        if (nome.startsWith("preview_")
            || nome.startsWith("thumb_")
            || nome.startsWith("hero_")) {
            return Categoria.DERIVADOS;
        }
        return Categoria.OUTROS;
    }

    private boolean ehProposta(Path relativo) {
        return relativo.getNameCount() >= 2
            && "propostas".equals(relativo.getName(0).toString());
    }

    private UUID agendamentoIdDoPrimeiroNivel(Path relativo) {
        if (relativo.getNameCount() < 2) {
            return null;
        }
        try {
            return UUID.fromString(relativo.getName(0).toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private long tamanho(Path caminho) {
        try {
            return Files.size(caminho);
        } catch (IOException e) {
            log.atDebug()
                .addKeyValue("event_action", "armazenamento.arquivo_ilegivel")
                .addKeyValue("caminho", caminho.toString())
                .log("Arquivo ignorado na medição de disco");
            return 0;
        }
    }

    private Volume volume(Path uploadDir) {
        try {
            FileStore store = Files.getFileStore(uploadDir);
            return new Volume(store.getTotalSpace(), store.getUsableSpace());
        } catch (IOException | UncheckedIOException e) {
            log.atWarn()
                .addKeyValue("event_action", "armazenamento.volume_indisponivel")
                .addKeyValue("upload_dir", uploadDir.toString())
                .setCause(e)
                .log("Não foi possível ler o espaço livre do volume de uploads");
            return new Volume(0, 0);
        }
    }

    private double percentualUso(Volume volume) {
        if (volume.total() <= 0) {
            return 0;
        }
        var usado = volume.total() - volume.livre();
        return Math.round(usado * 1000.0 / volume.total()) / 10.0;
    }

    /** Invalida a medição em cache após um expurgo (o disco mudou agora). */
    public void invalidarCache() {
        cacheUso = null;
    }

    private enum Categoria {
        FOTOS, DERIVADOS, EDITADAS, DOCUMENTOS, OUTROS
    }

    private record Contagem(long total, long arquivadas) {}

    private record Arquivamento(LocalDateTime em, long bytes) {}

    private record Volume(long total, long livre) {}

    private record CacheUso(UsoDisco uso, long expiraEm) {}

    private record Contexto(
        List<AgendamentoResumo> resumos,
        Map<UUID, Contagem> contagens,
        Map<UUID, LocalDateTime> ultimosDownloads,
        Map<UUID, Arquivamento> arquivamentos,
        Map<UUID, Long> pendencias,
        UsoDisco uso,
        Volume volume
    ) {}

    private record UsoDisco(
        long fotos,
        long derivados,
        long editadas,
        long documentos,
        long propostas,
        long outros,
        long total,
        Map<UUID, UsoGaleria> porGaleria
    ) {}

    private static final class UsoGaleria {
        private static final UsoGaleria VAZIA = new UsoGaleria();

        private long fotos;
        private long documentos;
        private long total;

        void somar(Categoria categoria, long bytes) {
            total += bytes;
            switch (categoria) {
                case FOTOS -> fotos += bytes;
                case DOCUMENTOS -> documentos += bytes;
                default -> { /* derivados/editadas/outros entram apenas no total */ }
            }
        }

        long fotos() {
            return fotos;
        }

        long documentos() {
            return documentos;
        }

        long total() {
            return total;
        }
    }
}
