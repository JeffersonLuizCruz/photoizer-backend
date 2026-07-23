package com.photoizer.crm.foto.service;

import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import com.photoizer.crm.shared.storage.FileStorageService;
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

    private static final String TEXTO_MARCA_DAGUA = "© Photoizer Studio";
    private static final float OPACIDADE_MARCA = 0.15f;

    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final FileStorageService fileStorageService;
    private final ImageProcessingService imageProcessingService;

    public FotoService(FotoEnsaioRepository fotoEnsaioRepository,
                       FileStorageService fileStorageService,
                       ImageProcessingService imageProcessingService) {
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.fileStorageService = fileStorageService;
        this.imageProcessingService = imageProcessingService;
    }

    @Transactional(readOnly = true)
    public List<FotoEnsaio> listar(UUID agendamentoId) {
        return fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId);
    }

    @Transactional(readOnly = true)
    public FotoEnsaio buscarPorId(UUID id) {
        return fotoEnsaioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Foto não encontrada: " + id));
    }

    public List<FotoEnsaio> uploadFotos(UUID agendamentoId, List<MultipartFile> arquivos) {
        var fotos = new ArrayList<FotoEnsaio>();
        var count = fotoEnsaioRepository.countByAgendamentoId(agendamentoId);

        for (int i = 0; i < arquivos.size(); i++) {
            var arquivo = arquivos.get(i);

            var originalPath = fileStorageService.salvarEmSubdiretorio(arquivo, agendamentoId, "orig");
            var original = Path.of(originalPath);
            var targetDir = original.getParent();

            String watermarkedPath;
            String thumbPath;
            try {
                var wm = imageProcessingService.aplicarMarcaDagua(original, targetDir, TEXTO_MARCA_DAGUA, OPACIDADE_MARCA);
                watermarkedPath = wm.toString();
            } catch (Exception e) {
                watermarkedPath = originalPath;
            }
            try {
                var thumb = imageProcessingService.gerarThumbnail(original, targetDir);
                thumbPath = thumb.toString();
            } catch (Exception e) {
                thumbPath = originalPath;
            }

            var foto = FotoEnsaio.builder()
                .agendamentoId(agendamentoId)
                .fileName(arquivo.getOriginalFilename())
                .originalPath(originalPath)
                .watermarkedPath(watermarkedPath)
                .thumbPath(thumbPath)
                .ordem(count + i)
                .status(StatusFoto.INEDITA)
                .selecionadaPacote(false)
                .build();

            fotos.add(fotoEnsaioRepository.save(foto));
        }

        return fotos;
    }

    public void deletar(UUID id) {
        var foto = buscarPorId(id);
        deletarArquivo(foto.getOriginalPath());
        deletarArquivo(foto.getWatermarkedPath());
        deletarArquivo(foto.getThumbPath());
        fotoEnsaioRepository.deleteById(id);
    }

    public List<FotoEnsaio> publicar(UUID agendamentoId) {
        var fotos = fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId);
        for (var foto : fotos) {
            foto.setStatus(StatusFoto.PUBLICADA);
        }
        return fotoEnsaioRepository.saveAll(fotos);
    }

    public FotoEnsaio atualizarOrdem(UUID id, int ordem) {
        var foto = buscarPorId(id);
        foto.setOrdem(ordem);
        return fotoEnsaioRepository.save(foto);
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

    private void deletarArquivo(String caminho) {
        try {
            Files.deleteIfExists(Path.of(caminho));
        } catch (IOException ignored) {}
    }
}
