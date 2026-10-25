package com.photoizer.crm.foto.service;

import com.photoizer.crm.foto.event.FotosRemovidasEvent;
import com.photoizer.crm.foto.exception.AgendamentoNaoPermitidoParaUploadException;
import com.photoizer.crm.foto.exception.ArquivoDeImagemInvalidoException;
import com.photoizer.crm.foto.exception.FotoEnsaioNaoEncontradaException;
import com.photoizer.crm.foto.exception.FotoNaoPertenceAoAgendamentoException;
import com.photoizer.crm.foto.exception.FotoVinculadaACompraException;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class FotoService {

    private static final Logger log = LoggerFactory.getLogger(FotoService.class);

    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final FileStorageService fileStorageService;
    private final FotoProcessingHelper fotoProcessingHelper;
    private final com.photoizer.crm.foto.acl.AgendamentoReadService agendamentoReadService;
    private final FileValidator fileValidator;
    private final StoragePathResolver pathResolver;
    private final ApplicationEventPublisher eventPublisher;

    public FotoService(FotoEnsaioRepository fotoEnsaioRepository,
                       FileStorageService fileStorageService,
                       FotoProcessingHelper fotoProcessingHelper,
                       com.photoizer.crm.foto.acl.AgendamentoReadService agendamentoReadService,
                       FileValidator fileValidator,
                       StoragePathResolver pathResolver,
                       ApplicationEventPublisher eventPublisher) {
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.fileStorageService = fileStorageService;
        this.fotoProcessingHelper = fotoProcessingHelper;
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
            || foto.getStatus() == StatusFoto.PAGA
            || foto.getStatus() == StatusFoto.AGUARDANDO_COMPROVANTE
            || foto.getStatus() == StatusFoto.AGUARDANDO_CONFIRMACAO;
    }

    public List<FotoEnsaio> publicar(UUID agendamentoId) {
        if (!agendamentoReadService.isStatusPermitidoParaPublicacao(agendamentoId)) {
            throw new com.photoizer.crm.foto.exception.AgendamentoNaoPermitidoParaPublicacaoException();
        }
        var fotos = fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId);
        var fotosParaPublicar = fotos.stream()
            .filter(f -> f.getStatus() == StatusFoto.INEDITA)
            .toList();
        for (var foto : fotosParaPublicar) {
            foto.setStatus(StatusFoto.PUBLICADA);
        }
        var publicadas = fotoEnsaioRepository.saveAll(fotosParaPublicar);
        log.atInfo()
            .addKeyValue("event_action", "foto.publicadas")
            .addKeyValue("agendamento_id", agendamentoId)
            .addKeyValue("quantidade", publicadas.size())
            .log("Fotos publicadas");
        return publicadas;
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
        var foto = buscarPorId(fotoId);
        if (!foto.getAgendamentoId().equals(agendamentoId)) {
            throw new FotoNaoPertenceAoAgendamentoException(fotoId, agendamentoId);
        }
        foto.setVisivel(visivel);
        return fotoEnsaioRepository.save(foto);
    }

    public FotoEnsaio alterarStatus(UUID agendamentoId, UUID fotoId, StatusFoto status) {
        var foto = buscarPorId(fotoId);
        if (!foto.getAgendamentoId().equals(agendamentoId)) {
            throw new FotoNaoPertenceAoAgendamentoException(fotoId, agendamentoId);
        }
        if (status == StatusFoto.PUBLICADA && !agendamentoReadService.isStatusPermitidoParaPublicacao(agendamentoId)) {
            throw new com.photoizer.crm.foto.exception.AgendamentoNaoPermitidoParaPublicacaoException();
        }
        foto.setStatus(foto.getStatus().transicionarPara(status));
        return fotoEnsaioRepository.save(foto);
    }

    public FotoEnsaio substituirImagem(UUID agendamentoId, UUID fotoId, MultipartFile arquivo) {
        var foto = buscarPorId(fotoId);
        if (!foto.getAgendamentoId().equals(agendamentoId)) {
            throw new FotoNaoPertenceAoAgendamentoException(fotoId, agendamentoId);
        }

        fileValidator.validate(arquivo, "image");
        var originalPath = fileStorageService.salvarEmSubdiretorio(arquivo, agendamentoId, "orig");
        var original = pathResolver.resolver(originalPath);
        var targetDir = original.getParent();

        var processada = fotoProcessingHelper.processar(original, targetDir, fotoId);

        deletarArquivo(foto.getOriginalPath());
        deletarArquivo(foto.getWatermarkedPath());
        deletarArquivo(foto.getThumbPath());

        foto.setOriginalPath(originalPath);
        foto.setWatermarkedPath(pathResolver.relativo(Path.of(processada.watermarkedPath())));
        foto.setThumbPath(pathResolver.relativo(Path.of(processada.thumbPath())));
        foto.setFileName(arquivo.getOriginalFilename());

        return fotoEnsaioRepository.save(foto);
    }

    private void deletarArquivo(String caminho) {
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
