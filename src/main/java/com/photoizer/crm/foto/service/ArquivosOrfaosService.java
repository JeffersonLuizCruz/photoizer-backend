package com.photoizer.crm.foto.service;

import com.photoizer.crm.foto.api.ArquivoOrfaoResponse;
import com.photoizer.crm.foto.api.ArquivosOrfaosResponse;
import com.photoizer.crm.foto.api.LimparOrfaosResponse;
import com.photoizer.crm.shared.storage.ArquivosReferenciadosProvider;
import com.photoizer.crm.shared.storage.StoragePathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detecta e remove arquivos órfãos do diretório de uploads.
 *
 * <p>Um arquivo é órfão quando o caminho relativo não aparece em nenhum dos
 * {@link ArquivosReferenciadosProvider} (fotos, comprovantes, contratos, logo,
 * pagamentos e despesas). Por segurança:
 * <ul>
 *   <li>só nomes reconhecidos são elegíveis para remoção;</li>
 *   <li>arquivos com menos de 24h são ignorados (upload em andamento);</li>
 *   <li>nomes desconhecidos são apenas listados, nunca apagados.</li>
 * </ul>
 */
@Service
public class ArquivosOrfaosService {

    private static final Logger log = LoggerFactory.getLogger(ArquivosOrfaosService.class);

    private static final Duration IDADE_MINIMA = Duration.ofHours(24);
    private static final int LIMITE_LISTAGEM = 200;

    private final StoragePathResolver pathResolver;
    private final List<ArquivosReferenciadosProvider> providers;
    private final ArmazenamentoService armazenamentoService;

    public ArquivosOrfaosService(StoragePathResolver pathResolver,
                                 List<ArquivosReferenciadosProvider> providers,
                                 ArmazenamentoService armazenamentoService) {
        this.pathResolver = pathResolver;
        this.providers = providers;
        this.armazenamentoService = armazenamentoService;
    }

    public ArquivosOrfaosResponse listar() {
        var uploadDir = pathResolver.getUploadDir();
        var orfaos = varrer(uploadDir);
        var bytes = orfaos.stream().mapToLong(ArquivoOrfao::bytes).sum();
        var elegiveis = orfaos.stream().filter(ArquivoOrfao::elegivel).toList();
        var bytesElegiveis = elegiveis.stream().mapToLong(ArquivoOrfao::bytes).sum();
        var desconhecidos = (int) orfaos.stream().filter(orfao -> !orfao.reconhecido()).count();
        var listados = orfaos.stream().limit(LIMITE_LISTAGEM).map(this::toResponse).toList();

        return new ArquivosOrfaosResponse(
            orfaos.size(),
            bytes,
            elegiveis.size(),
            bytesElegiveis,
            desconhecidos,
            listados.size(),
            orfaos.size() > LIMITE_LISTAGEM,
            listados
        );
    }

    public LimparOrfaosResponse limpar() {
        var uploadDir = pathResolver.getUploadDir();
        var elegiveis = varrer(uploadDir).stream()
            .filter(ArquivoOrfao::elegivel)
            .toList();

        var arquivos = 0;
        long bytes = 0;
        for (var orfao : elegiveis) {
            try {
                if (Files.deleteIfExists(uploadDir.resolve(orfao.caminho()))) {
                    arquivos++;
                    bytes += orfao.bytes();
                }
            } catch (IOException e) {
                log.atWarn()
                    .addKeyValue("event_action", "armazenamento.orfao_delete_falhou")
                    .addKeyValue("caminho", orfao.caminho())
                    .setCause(e)
                    .log("Falha ao apagar arquivo órfão");
            }
        }

        armazenamentoService.invalidarCache();
        log.atInfo()
            .addKeyValue("event_action", "armazenamento.orfaos_limpos")
            .addKeyValue("arquivos", arquivos)
            .addKeyValue("bytes_liberados", bytes)
            .log("Arquivos órfãos removidos");
        return new LimparOrfaosResponse(arquivos, bytes);
    }

    private List<ArquivoOrfao> varrer(Path uploadDir) {
        if (!Files.isDirectory(uploadDir)) {
            return List.of();
        }
        var referenciados = caminhosReferenciados(uploadDir);
        var limite = Instant.now().minus(IDADE_MINIMA);
        var orfaos = new ArrayList<ArquivoOrfao>();

        try (var arquivos = Files.walk(uploadDir)) {
            for (var caminho : (Iterable<Path>) arquivos.filter(Files::isRegularFile)::iterator) {
                var relativo = relativo(caminho, uploadDir);
                if (referenciados.contains(relativo)) {
                    continue;
                }
                var modificado = modificadoEm(caminho);
                var reconhecido = reconhecido(relativo);
                orfaos.add(new ArquivoOrfao(
                    relativo,
                    tamanho(caminho),
                    modificado,
                    reconhecido,
                    reconhecido && modificado.isBefore(limite)
                ));
            }
        } catch (IOException | UncheckedIOException e) {
            log.atWarn()
                .addKeyValue("event_action", "armazenamento.orfaos_varredura_falhou")
                .addKeyValue("upload_dir", uploadDir.toString())
                .setCause(e)
                .log("Falha ao procurar arquivos órfãos");
        }

        orfaos.sort(Comparator.comparingLong(ArquivoOrfao::bytes).reversed());
        return orfaos;
    }

    private Set<String> caminhosReferenciados(Path uploadDir) {
        var referenciados = new HashSet<String>();
        for (var provider : providers) {
            for (var caminho : provider.caminhosReferenciados()) {
                var normalizado = normalizar(caminho, uploadDir);
                if (normalizado != null) {
                    referenciados.add(normalizado);
                }
            }
        }
        return referenciados;
    }

    private String normalizar(String caminho, Path uploadDir) {
        if (caminho == null || caminho.isBlank()) {
            return null;
        }
        try {
            var path = Path.of(caminho);
            if (path.isAbsolute()) {
                var absoluto = path.normalize();
                return absoluto.startsWith(uploadDir) ? relativo(absoluto, uploadDir) : null;
            }
            return path.normalize().toString().replace('\\', '/');
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private String relativo(Path caminho, Path uploadDir) {
        return uploadDir.relativize(caminho).toString().replace('\\', '/');
    }

    /**
     * Só remove arquivos com nomes gerados pelo próprio sistema. Qualquer nome
     * fora dos padrões abaixo é tratado como desconhecido e preservado.
     */
    private boolean reconhecido(String caminho) {
        var nome = caminho.substring(caminho.lastIndexOf('/') + 1);
        if (caminho.startsWith("propostas/") && nome.toLowerCase().endsWith(".pdf")) {
            return true;
        }
        return nome.startsWith("orig_")
            || nome.startsWith("wm_")
            || nome.startsWith("thumb_")
            || nome.startsWith("preview_")
            || nome.startsWith("hero_")
            || nome.startsWith("editadas_")
            || nome.startsWith("comprovante_");
    }

    private long tamanho(Path caminho) {
        try {
            return Files.size(caminho);
        } catch (IOException e) {
            return 0;
        }
    }

    private Instant modificadoEm(Path caminho) {
        try {
            return Files.getLastModifiedTime(caminho).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    private ArquivoOrfaoResponse toResponse(ArquivoOrfao orfao) {
        var modificado = orfao.modificadoEm() == null
            ? null
            : LocalDateTime.ofInstant(orfao.modificadoEm(), ZoneId.systemDefault());
        return new ArquivoOrfaoResponse(
            orfao.caminho(),
            orfao.bytes(),
            modificado,
            orfao.reconhecido(),
            orfao.elegivel()
        );
    }

    private record ArquivoOrfao(
        String caminho,
        long bytes,
        Instant modificadoEm,
        boolean reconhecido,
        boolean elegivel
    ) {}
}
