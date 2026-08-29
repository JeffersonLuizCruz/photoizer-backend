package com.photoizer.crm.foto.service;

import com.photoizer.crm.foto.exception.AgendamentoNaoPermitidoParaUploadException;
import com.photoizer.crm.foto.exception.FotoEnsaioNaoEncontradaException;
import com.photoizer.crm.foto.exception.FotoNaoPertenceAoAgendamentoException;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import com.photoizer.crm.shared.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class FotoService {

    private static final Logger log = LoggerFactory.getLogger(FotoService.class);

    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final FileStorageService fileStorageService;
    private final FotoProcessingHelper fotoProcessingHelper;
    private final com.photoizer.crm.foto.acl.AgendamentoReadService agendamentoReadService;

    public FotoService(FotoEnsaioRepository fotoEnsaioRepository,
                       FileStorageService fileStorageService,
                       FotoProcessingHelper fotoProcessingHelper,
                       com.photoizer.crm.foto.acl.AgendamentoReadService agendamentoReadService) {
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.fileStorageService = fileStorageService;
        this.fotoProcessingHelper = fotoProcessingHelper;
        this.agendamentoReadService = agendamentoReadService;
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
        var count = fotoEnsaioRepository.findMaxOrdemByAgendamentoId(agendamentoId) + 1;

        for (int i = 0; i < arquivos.size(); i++) {
            var arquivo = arquivos.get(i);

            var originalPath = fileStorageService.salvarEmSubdiretorio(arquivo, agendamentoId, "orig");
            var original = Path.of(originalPath);
            var targetDir = original.getParent();

            var processada = fotoProcessingHelper.processar(original, targetDir, UUID.randomUUID());

            var fileName = arquivo.getOriginalFilename();
            if (fileName == null || fileName.isBlank()) {
                fileName = "foto_" + UUID.randomUUID() + ".jpg";
            }

            var foto = FotoEnsaio.builder()
                .agendamentoId(agendamentoId)
                .fileName(fileName)
                .originalPath(originalPath)
                .watermarkedPath(processada.watermarkedPath())
                .thumbPath(processada.thumbPath())
                .ordem(count + i)
                .status(StatusFoto.INEDITA)
                .selecionadaPacote(false)
                .visivel(true)
                .build();

            fotos.add(fotoEnsaioRepository.save(foto));
        }

        return fotos;
    }

    public void deletar(UUID agendamentoId, UUID id) {
        var foto = buscarPorId(id);
        if (!foto.getAgendamentoId().equals(agendamentoId)) {
            throw new FotoNaoPertenceAoAgendamentoException(id, agendamentoId);
        }
        deletarArquivo(foto.getOriginalPath());
        deletarArquivo(foto.getWatermarkedPath());
        deletarArquivo(foto.getThumbPath());
        fotoEnsaioRepository.deleteById(id);
    }

    public List<FotoEnsaio> publicar(UUID agendamentoId) {
        var fotos = fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId);
        var fotosParaPublicar = fotos.stream()
            .filter(f -> f.getStatus() == StatusFoto.INEDITA)
            .toList();
        for (var foto : fotosParaPublicar) {
            foto.setStatus(StatusFoto.PUBLICADA);
        }
        return fotoEnsaioRepository.saveAll(fotosParaPublicar);
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
        foto.setStatus(foto.getStatus().transicionarPara(status));
        return fotoEnsaioRepository.save(foto);
    }

    public FotoEnsaio substituirImagem(UUID agendamentoId, UUID fotoId, MultipartFile arquivo) {
        var foto = buscarPorId(fotoId);
        if (!foto.getAgendamentoId().equals(agendamentoId)) {
            throw new FotoNaoPertenceAoAgendamentoException(fotoId, agendamentoId);
        }

        var originalPath = fileStorageService.salvarEmSubdiretorio(arquivo, agendamentoId, "orig");
        var original = Path.of(originalPath);
        var targetDir = original.getParent();

        var processada = fotoProcessingHelper.processar(original, targetDir, fotoId);

        deletarArquivo(foto.getOriginalPath());
        deletarArquivo(foto.getWatermarkedPath());
        deletarArquivo(foto.getThumbPath());

        foto.setOriginalPath(originalPath);
        foto.setWatermarkedPath(processada.watermarkedPath());
        foto.setThumbPath(processada.thumbPath());
        foto.setFileName(arquivo.getOriginalFilename());

        return fotoEnsaioRepository.save(foto);
    }

    private void deletarArquivo(String caminho) {
        try {
            Files.deleteIfExists(Path.of(caminho));
        } catch (IOException e) {
            log.warn("Falha ao deletar arquivo: {}", caminho, e);
        }
    }
}
