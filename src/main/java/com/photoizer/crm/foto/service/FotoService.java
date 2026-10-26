package com.photoizer.crm.foto.service;

import com.photoizer.crm.foto.event.FotoEditadaEvent;
import com.photoizer.crm.foto.event.FotosRemovidasEvent;
import com.photoizer.crm.foto.exception.AgendamentoNaoPermitidoParaUploadException;
import com.photoizer.crm.foto.exception.ArquivoDeImagemInvalidoException;
import com.photoizer.crm.foto.exception.FotoEnsaioNaoEncontradaException;
import com.photoizer.crm.foto.exception.FotoNaoEditavelException;
import com.photoizer.crm.foto.exception.FotoNaoPertenceAoAgendamentoException;
import com.photoizer.crm.foto.exception.FotoVinculadaACompraException;
import com.photoizer.crm.foto.exception.RenumeracaoNaoPermitidaException;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import com.photoizer.crm.shared.storage.FileStorageService;
import com.photoizer.crm.shared.storage.FileValidator;
import com.photoizer.crm.shared.storage.StoragePathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Transactional
public class FotoService {

    private static final Logger log = LoggerFactory.getLogger(FotoService.class);

    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final FileStorageService fileStorageService;
    private final FotoProcessingHelper fotoProcessingHelper;
    private final FotoPreviewService fotoPreviewService;
    private final com.photoizer.crm.foto.acl.AgendamentoReadService agendamentoReadService;
    private final FileValidator fileValidator;
    private final StoragePathResolver pathResolver;
    private final ApplicationEventPublisher eventPublisher;

    public FotoService(FotoEnsaioRepository fotoEnsaioRepository,
                       FileStorageService fileStorageService,
                       FotoProcessingHelper fotoProcessingHelper,
                       FotoPreviewService fotoPreviewService,
                       com.photoizer.crm.foto.acl.AgendamentoReadService agendamentoReadService,
                       FileValidator fileValidator,
                       StoragePathResolver pathResolver,
                       ApplicationEventPublisher eventPublisher) {
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.fileStorageService = fileStorageService;
        this.fotoProcessingHelper = fotoProcessingHelper;
        this.fotoPreviewService = fotoPreviewService;
        this.agendamentoReadService = agendamentoReadService;
        this.fileValidator = fileValidator;
        this.pathResolver = pathResolver;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<FotoEnsaio> listar(UUID agendamentoId) {
        return fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId);
    }

    @Transactional(readOnly = true)
    public List<FotoEnsaio> listarPorCompraExtraId(UUID compraExtraId) {
        return fotoEnsaioRepository.findByCompraExtraId(compraExtraId);
    }

    @Transactional(readOnly = true)
    public FotoEnsaio buscarPorId(UUID id) {
        return fotoEnsaioRepository.findById(id)
            .orElseThrow(() -> new FotoEnsaioNaoEncontradaException(id));
    }

    public List<FotoEnsaio> uploadFotos(UUID agendamentoId, List<MultipartFile> arquivos) {
        if (!agendamentoReadService.isStatusPermitidoParaUpload(agendamentoId)) {
            throw new AgendamentoNaoPermitidoParaUploadException();
        }
        if (temSelecaoAtiva(agendamentoId)) {
            throw new FotoNaoEditavelException("a galeria já possui seleção enviada para edição");
        }

        var fotos = new ArrayList<FotoEnsaio>();
        var arquivosGravados = new ArrayList<Path>();
        var count = fotoEnsaioRepository.findMaxOrdemByAgendamentoId(agendamentoId) + 1;

        try {
            for (int i = 0; i < arquivos.size(); i++) {
                var arquivo = arquivos.get(i);

                fileValidator.validate(arquivo, "image");

                var originalPath = fileStorageService.salvarEmSubdiretorio(arquivo, agendamentoId, "orig");
                var original = pathResolver.resolver(originalPath);
                arquivosGravados.add(original);
                validarImagemLegivel(original, arquivo.getOriginalFilename());
                var targetDir = original.getParent();

                FotoProcessingHelper.ProcessedImages processada;
                try {
                    processada = fotoProcessingHelper.processar(original, targetDir, UUID.randomUUID());
                } catch (IllegalArgumentException e) {
                    // Arquivo com cabeçalho de imagem mas conteúdo corrompido.
                    throw new ArquivoDeImagemInvalidoException(arquivo.getOriginalFilename(), e);
                }
                arquivosGravados.add(Path.of(processada.watermarkedPath()));
                arquivosGravados.add(Path.of(processada.thumbPath()));
                if (processada.previewPath() != null) {
                    arquivosGravados.add(Path.of(processada.previewPath()));
                }

                var fileName = arquivo.getOriginalFilename();
                if (fileName == null || fileName.isBlank()) {
                    fileName = "foto_" + UUID.randomUUID() + ".jpg";
                }

                var foto = FotoEnsaio.builder()
                    .agendamentoId(agendamentoId)
                    .fileName(fileName)
                    .originalPath(originalPath)
                    .watermarkedPath(pathResolver.relativo(Path.of(processada.watermarkedPath())))
                    .thumbPath(pathResolver.relativo(Path.of(processada.thumbPath())))
                    .previewPath(processada.previewPath() == null
                        ? null
                        : pathResolver.relativo(Path.of(processada.previewPath())))
                    .ordem(count + i)
                    .status(StatusFoto.INEDITA)
                    .selecionadaPacote(false)
                    .visivel(true)
                    .build();

                fotos.add(fotoEnsaioRepository.save(foto));
            }

            log.atInfo()
                .addKeyValue("event_action", "foto.upload_concluido")
                .addKeyValue("agendamento_id", agendamentoId)
                .addKeyValue("quantidade", fotos.size())
                .log("Upload de fotos concluído");

            return fotos;
        } catch (RuntimeException e) {
            // A transação sofrerá rollback: remove os arquivos gravados nesta
            // requisição para não acumular órfãos no volume de uploads.
            limparArquivos(arquivosGravados);
            throw e;
        }
    }

    /**
     * Confere que o arquivo salvo é uma imagem em um formato que o ImageIO
     * consegue decodificar. Evita que um arquivo renomeado/corrompido derrube
     * o lote inteiro com um erro genérico — a mensagem inclui o nome original.
     *
     * <p>Valida o arquivo já salvo para não exigir uma segunda cópia do
     * conteúdo em memória/temporário (uma foto pode ter ~50MB).
     */
    private void validarImagemLegivel(Path arquivoSalvo, String nomeOriginal) {
        try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(arquivoSalvo.toFile())) {
            var readers = imageInputStream == null ? null : ImageIO.getImageReaders(imageInputStream);
            if (readers == null || !readers.hasNext()) {
                throw new ArquivoDeImagemInvalidoException(nomeOriginal);
            }
            readers.next().dispose();
        } catch (IOException e) {
            throw new ArquivoDeImagemInvalidoException(nomeOriginal, e);
        }
    }

    private void limparArquivos(List<Path> caminhos) {
        for (var caminho : caminhos) {
            try {
                Files.deleteIfExists(caminho);
            } catch (IOException e) {
                log.atWarn()
                    .addKeyValue("event_action", "foto.limpeza_arquivo_falhou")
                    .addKeyValue("caminho", caminho.toString())
                    .setCause(e)
                    .log("Falha ao remover arquivo de upload abortado");
            }
        }
    }

    public void deletar(UUID agendamentoId, UUID id) {
        deletarEmLote(agendamentoId, List.of(id));
    }

    /**
     * Remove várias fotos de um agendamento de forma atômica: valida todas
     * antes de apagar qualquer arquivo ou registro. Fotos vinculadas a uma
     * compra (pendente ou paga) ou já incluídas no pacote são protegidas.
     *
     * @return quantidade de fotos removidas
     */
    public int deletarEmLote(UUID agendamentoId, List<UUID> fotoIds) {
        var ids = new LinkedHashSet<>(fotoIds);
        var fotos = fotoEnsaioRepository.findAllById(ids);

        if (fotos.size() != ids.size()) {
            var encontrados = fotos.stream().map(FotoEnsaio::getId).collect(Collectors.toSet());
            var ausente = ids.stream()
                .filter(id -> !encontrados.contains(id))
                .findFirst()
                .orElseThrow();
            throw new FotoEnsaioNaoEncontradaException(ausente);
        }

        for (var foto : fotos) {
            if (!foto.getAgendamentoId().equals(agendamentoId)) {
                throw new FotoNaoPertenceAoAgendamentoException(foto.getId(), agendamentoId);
            }
        }

        var protegidas = fotos.stream().filter(this::estaVinculadaACompra).count();
        if (protegidas > 0) {
            throw new FotoVinculadaACompraException((int) protegidas);
        }

        for (var foto : fotos) {
            deletarArquivo(foto.getOriginalPath());
            deletarArquivo(foto.getWatermarkedPath());
            deletarArquivo(foto.getThumbPath());
            deletarArquivo(foto.getPreviewPath());
            deletarArquivo(foto.getEditadaPath());
            deletarArquivo(foto.getEditadaWatermarkedPath());
            deletarArquivo(foto.getEditadaThumbPath());
            deletarArquivo(foto.getEditadaPreviewPath());
            fotoPreviewService.removerLegados(foto);
        }
        fotoEnsaioRepository.deleteAll(fotos);

        eventPublisher.publishEvent(new FotosRemovidasEvent(agendamentoId, List.copyOf(ids)));

        log.atInfo()
            .addKeyValue("event_action", "foto.deletadas_em_lote")
            .addKeyValue("agendamento_id", agendamentoId)
            .addKeyValue("quantidade", fotos.size())
            .log("Fotos deletadas em lote");

        return fotos.size();
    }

    private boolean estaVinculadaACompra(FotoEnsaio foto) {
        return foto.getCompraExtraId() != null
            || foto.isSelecionadaPacote()
            || foto.isEmSelecaoEdicao()
            || foto.getEditadaPath() != null
            || foto.getDataDownload() != null
            || foto.getStatus() == StatusFoto.PAGA
            || foto.getStatus() == StatusFoto.AGUARDANDO_COMPROVANTE
            || foto.getStatus() == StatusFoto.AGUARDANDO_CONFIRMACAO;
    }

    /**
     * Publica as fotos pendentes e garante a numeracao sequencial da galeria.
     *
     * <p>A numeracao e atribuida na ordem de exibicao ({@code ordem}), e uma
     * foto ja numerada nunca e renumerada. Fotos ocultas nao recebem numero:
     * elas so serao numeradas quando forem exibidas.
     */
    public List<FotoEnsaio> publicar(UUID agendamentoId) {
        if (!agendamentoReadService.isStatusPermitidoParaPublicacao(agendamentoId)) {
            throw new com.photoizer.crm.foto.exception.AgendamentoNaoPermitidoParaPublicacaoException();
        }
        // A lock pessimista serializa publicacoes concorrentes do mesmo ensaio.
        var fotos = fotoEnsaioRepository.findAllByAgendamentoIdForUpdate(agendamentoId);
        if (fotos.stream().anyMatch(FotoEnsaio::isEmSelecaoEdicao)) {
            throw new FotoNaoEditavelException("a galeria já possui seleção enviada para edição");
        }
        var proximoNumero = new AtomicInteger(proximoNumeroGaleria(fotos));
        var alteradas = new ArrayList<FotoEnsaio>();

        for (var foto : fotos) {
            var alterou = false;
            if (foto.getStatus() == StatusFoto.INEDITA) {
                foto.setStatus(StatusFoto.PUBLICADA);
                alterou = true;
            }
            if (foto.getStatus() == StatusFoto.PUBLICADA
                && foto.isVisivel()
                && foto.getNumeroGaleria() == null) {
                foto.setNumeroGaleria(proximoNumero.getAndIncrement());
                alterou = true;
            }
            if (alterou) {
                alteradas.add(foto);
            }
        }

        var publicadas = fotoEnsaioRepository.saveAll(alteradas);
        log.atInfo()
            .addKeyValue("event_action", "foto.publicadas")
            .addKeyValue("agendamento_id", agendamentoId)
            .addKeyValue("quantidade", publicadas.size())
            .addKeyValue("ultimo_numero", publicadas.stream()
                .map(FotoEnsaio::getNumeroGaleria)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0))
            .log("Fotos publicadas e numeradas");
        return publicadas;
    }

    /**
     * Reatribui a numeracao 1..N as fotos publicadas e visiveis.
     *
     * <p>Operacao explicita e destrutiva para a referencia visual: so pode
     * ocorrer antes de qualquer foto ser selecionada, baixada, comprada ou
     * vinculada a uma compra. Depois disso, os numeros sao imutaveis.
     */
    public List<FotoEnsaio> renumerar(UUID agendamentoId) {
        var fotos = fotoEnsaioRepository.findAllByAgendamentoIdForUpdate(agendamentoId);
        var bloqueadas = fotos.stream().filter(this::numeracaoBloqueada).count();
        if (bloqueadas > 0) {
            throw new RenumeracaoNaoPermitidaException((int) bloqueadas);
        }

        var fotosNumeraveis = fotos.stream()
            .filter(f -> f.getStatus() == StatusFoto.PUBLICADA && f.isVisivel())
            .sorted(Comparator.comparingInt(FotoEnsaio::getOrdem)
                .thenComparing(FotoEnsaio::getId))
            .toList();

        var novosNumeros = new HashMap<UUID, Integer>();
        var numero = 1;
        for (var foto : fotosNumeraveis) {
            novosNumeros.put(foto.getId(), numero++);
        }

        for (var foto : fotos) {
            foto.setNumeroGaleria(novosNumeros.get(foto.getId()));
        }

        var salvas = fotoEnsaioRepository.saveAll(fotos);
        log.atInfo()
            .addKeyValue("event_action", "foto.galeria_renumerada")
            .addKeyValue("agendamento_id", agendamentoId)
            .addKeyValue("quantidade", fotosNumeraveis.size())
            .log("Galeria renumerada");
        return salvas;
    }

    /**
     * Atualiza metadados da foto: título, descrição, tags, categoria,
     * data da sessão e marcação de destaque (RF017).
     */
    public FotoEnsaio atualizarMetadata(UUID id, com.photoizer.crm.foto.api.FotoMetadataRequest request) {
        var foto = buscarPorId(id);
        if (request.titulo() != null) foto.setTitulo(request.titulo());
        if (request.descricao() != null) foto.setDescricao(request.descricao());
        if (request.tags() != null) foto.setTags(new ArrayList<>(request.tags()));
        if (request.categoria() != null) foto.setCategoria(request.categoria());
        if (request.dataSessao() != null) foto.setDataSessao(request.dataSessao());
        if (request.destaque() != null) foto.setDestaque(request.destaque());
        return fotoEnsaioRepository.save(foto);
    }

    public FotoEnsaio alterarVisibilidade(UUID agendamentoId, UUID fotoId, boolean visivel) {
        var fotos = fotoEnsaioRepository.findAllByAgendamentoIdForUpdate(agendamentoId);
        var foto = fotos.stream()
            .filter(f -> f.getId().equals(fotoId))
            .findFirst()
            .orElseThrow(() -> new FotoNaoPertenceAoAgendamentoException(fotoId, agendamentoId));

        // Ocultar uma foto vinculada a pacote/compra/edição/download remove o
        // acesso do cliente à galeria. Exibir novamente continua permitido
        // para que o staff consiga corrigir um estado oculto indevido.
        if (!visivel && estaVinculadaACompra(foto)) {
            throw new FotoNaoEditavelException(
                "foto vinculada a pacote, compra, download ou edição não pode ser ocultada");
        }
        foto.setVisivel(visivel);
        if (visivel) {
            atribuirNumeroSeNecessario(foto, fotos);
        }
        return fotoEnsaioRepository.save(foto);
    }

    public FotoEnsaio alterarStatus(UUID agendamentoId, UUID fotoId, StatusFoto status) {
        var fotos = fotoEnsaioRepository.findAllByAgendamentoIdForUpdate(agendamentoId);
        var foto = fotos.stream()
            .filter(f -> f.getId().equals(fotoId))
            .findFirst()
            .orElseThrow(() -> new FotoNaoPertenceAoAgendamentoException(fotoId, agendamentoId));

        if (status == StatusFoto.PUBLICADA && !agendamentoReadService.isStatusPermitidoParaPublicacao(agendamentoId)) {
            throw new com.photoizer.crm.foto.exception.AgendamentoNaoPermitidoParaPublicacaoException();
        }
        if (foto.isEmSelecaoEdicao()) {
            throw new FotoNaoEditavelException("foto faz parte de uma seleção ativa");
        }
        // Alterar o status de uma foto já entregue/comprada/baixada/em edição
        // pode retirá-la da galeria do cliente ou reverter uma venda. O staff
        // deve corrigir o vínculo antes de mexer no status.
        if (estaVinculadaACompra(foto)) {
            throw new FotoNaoEditavelException(
                "foto vinculada a pacote, compra, download ou edição não pode ter o status alterado");
        }
        foto.setStatus(foto.getStatus().transicionarPara(status));
        if (foto.getStatus() == StatusFoto.PUBLICADA && foto.isVisivel()) {
            atribuirNumeroSeNecessario(foto, fotos);
        }
        return fotoEnsaioRepository.save(foto);
    }

    public FotoEnsaio substituirImagem(UUID agendamentoId, UUID fotoId, MultipartFile arquivo) {
        var foto = buscarPorId(fotoId);
        if (!foto.getAgendamentoId().equals(agendamentoId)) {
            throw new FotoNaoPertenceAoAgendamentoException(fotoId, agendamentoId);
        }
        if (estaVinculadaACompra(foto)
            || foto.getVersaoAtual() == com.photoizer.crm.foto.model.VersaoFoto.EDITADA) {
            throw new FotoNaoEditavelException("foto vinculada a seleção, edição, compra ou download");
        }

        fileValidator.validate(arquivo, "image");
        var originalPath = fileStorageService.salvarEmSubdiretorio(arquivo, agendamentoId, "orig");
        var original = pathResolver.resolver(originalPath);
        var targetDir = original.getParent();

        var processada = fotoProcessingHelper.processar(original, targetDir, fotoId);

        deletarArquivo(foto.getOriginalPath());
        deletarArquivo(foto.getWatermarkedPath());
        deletarArquivo(foto.getThumbPath());
        deletarArquivo(foto.getPreviewPath());
        // A imagem mudou: invalida prévias protegidas em cache (legado/thumb).
        fotoPreviewService.removerLegados(foto);

        foto.setOriginalPath(originalPath);
        foto.setWatermarkedPath(pathResolver.relativo(Path.of(processada.watermarkedPath())));
        foto.setThumbPath(pathResolver.relativo(Path.of(processada.thumbPath())));
        foto.setPreviewPath(processada.previewPath() == null
            ? null
            : pathResolver.relativo(Path.of(processada.previewPath())));
        // O nome de exibicao e imutavel: a substituicao troca apenas o arquivo.
        // Assim o cliente e o staff continuam reconhecendo a mesma foto
        // (#001 · IMG_1234.jpg) mesmo apos uma troca de imagem.
        return fotoEnsaioRepository.save(foto);
    }

    /**
     * Salva a versão editada de uma foto existente, preservando o RAW e a
     * identidade da foto (id, numero de galeria e nome de exibição).
     */
    public FotoEnsaio uploadEditada(UUID agendamentoId, UUID fotoId,
                                    org.springframework.web.multipart.MultipartFile arquivo) {
        if (!agendamentoReadService.isStatusPermitidoParaUpload(agendamentoId)) {
            throw new AgendamentoNaoPermitidoParaUploadException();
        }

        var foto = buscarPorId(fotoId);
        if (!foto.getAgendamentoId().equals(agendamentoId)) {
            throw new FotoNaoPertenceAoAgendamentoException(fotoId, agendamentoId);
        }
        if (foto.getStatus() != StatusFoto.PUBLICADA) {
            throw new FotoNaoEditavelException("status atual " + foto.getStatus());
        }
        if (foto.getCompraExtraId() != null
            || foto.getDataDownload() != null
            || foto.getVersaoAtual() == com.photoizer.crm.foto.model.VersaoFoto.EDITADA
            || foto.getStatus() == StatusFoto.PAGA
            || foto.getStatus() == StatusFoto.AGUARDANDO_COMPROVANTE
            || foto.getStatus() == StatusFoto.AGUARDANDO_CONFIRMACAO) {
            throw new FotoNaoEditavelException("foto já vendida, baixada ou com edição publicada");
        }

        fileValidator.validate(arquivo, "image");
        var arquivosNovos = new ArrayList<Path>();
        var caminhosAntigos = java.util.stream.Stream.of(
                foto.getEditadaPath(),
                foto.getEditadaWatermarkedPath(),
                foto.getEditadaThumbPath(),
                foto.getEditadaPreviewPath())
            .filter(java.util.Objects::nonNull)
            .toList();

        try {
            var editadaPath = fileStorageService.salvarEmSubdiretorio(arquivo, agendamentoId, "editadas");
            var editada = pathResolver.resolver(editadaPath);
            arquivosNovos.add(editada);
            validarImagemLegivel(editada, arquivo.getOriginalFilename());

            var processada = fotoProcessingHelper.processar(editada, editada.getParent(), fotoId);
            arquivosNovos.add(Path.of(processada.watermarkedPath()));
            arquivosNovos.add(Path.of(processada.thumbPath()));
            if (processada.previewPath() != null) {
                arquivosNovos.add(Path.of(processada.previewPath()));
            }

            foto.setEditadaPath(editadaPath);
            foto.setEditadaWatermarkedPath(pathResolver.relativo(Path.of(processada.watermarkedPath())));
            foto.setEditadaThumbPath(pathResolver.relativo(Path.of(processada.thumbPath())));
            foto.setEditadaPreviewPath(processada.previewPath() == null
                ? null
                : pathResolver.relativo(Path.of(processada.previewPath())));
            foto.setEditadaFileName(fileValidator.sanitizeFilename(arquivo.getOriginalFilename()));
            foto.setEditadaEm(LocalDateTime.now());
            foto.setEditadaPor(usuarioAtual());
            var salva = fotoEnsaioRepository.save(foto);

            eventPublisher.publishEvent(new FotoEditadaEvent(agendamentoId, fotoId));

            // Remove as versões editadas anteriores somente depois que a nova
            // foi gravada e o snapshot foi atualizado com sucesso.
            caminhosAntigos.forEach(this::deletarArquivo);
            fotoPreviewService.removerLegados(foto);

            log.atInfo()
                .addKeyValue("event_action", "foto.editada_upload")
                .addKeyValue("agendamento_id", agendamentoId)
                .addKeyValue("foto_id", fotoId)
                .log("Versão editada enviada");
            return salva;
        } catch (RuntimeException e) {
            limparArquivos(arquivosNovos);
            throw e;
        }
    }

    private String usuarioAtual() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication();
        return authentication != null && authentication.getName() != null
            ? authentication.getName()
            : "SYSTEM";
    }

    private boolean temSelecaoAtiva(UUID agendamentoId) {
        var fotos = fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId);
        return fotos != null && fotos.stream().anyMatch(FotoEnsaio::isEmSelecaoEdicao);
    }

    private int proximoNumeroGaleria(List<FotoEnsaio> fotos) {
        return fotos.stream()
            .map(FotoEnsaio::getNumeroGaleria)
            .filter(java.util.Objects::nonNull)
            .max(Integer::compareTo)
            .orElse(0) + 1;
    }

    private void atribuirNumeroSeNecessario(FotoEnsaio foto, List<FotoEnsaio> fotos) {
        if (foto.getStatus() == StatusFoto.PUBLICADA
            && foto.isVisivel()
            && foto.getNumeroGaleria() == null) {
            foto.setNumeroGaleria(proximoNumeroGaleria(fotos));
        }
    }

    /**
     * Bloqueia a renumeracao quando qualquer numero ja pode ter sido visto,
     * baixado, comprado ou vinculado a uma selecao/compra.
     */
    private boolean numeracaoBloqueada(FotoEnsaio foto) {
        return foto.isSelecionadaPacote()
            || foto.isEmSelecaoEdicao()
            || foto.getEditadaPath() != null
            || foto.getDataDownload() != null
            || foto.getCompraExtraId() != null
            || foto.getStatus() == StatusFoto.PAGA
            || foto.getStatus() == StatusFoto.AGUARDANDO_COMPROVANTE
            || foto.getStatus() == StatusFoto.AGUARDANDO_CONFIRMACAO;
    }

    private void deletarArquivo(String caminho) {
        if (caminho == null || caminho.isBlank()) {
            return;
        }
        try {
            var path = pathResolver.resolver(caminho);
            if (path != null) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.atWarn()
                .addKeyValue("event_action", "foto.delecao_arquivo_falhou")
                .addKeyValue("caminho", caminho)
                .setCause(e)
                .log("Falha ao deletar arquivo");
        }
    }
}
