package com.photoizer.crm.foto.service;

import com.photoizer.crm.foto.model.FotoEnsaio;
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

            try {
                var watermarkedPath = imageProcessingService.aplicarMarcaDagua(original, targetDir, TEXTO_MARCA_DAGUA, OPACIDADE_MARCA);
                var thumbPath = imageProcessingService.gerarThumbnail(original, targetDir);

                var foto = FotoEnsaio.builder()
                    .agendamentoId(agendamentoId)
                    .fileName(arquivo.getOriginalFilename())
                    .originalPath(originalPath)
                    .watermarkedPath(watermarkedPath.toString())
                    .thumbPath(thumbPath.toString())
                    .ordem(count + i)
                    .status("INEDITA")
                    .selecionadaPacote(false)
                    .build();

                fotos.add(fotoEnsaioRepository.save(foto));
            } catch (IOException e) {
                throw new RuntimeException("Erro ao processar imagem: " + arquivo.getOriginalFilename(), e);
            }
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
            foto.setStatus("PUBLICADA");
        }
        return fotoEnsaioRepository.saveAll(fotos);
    }

    public FotoEnsaio atualizarOrdem(UUID id, int ordem) {
        var foto = buscarPorId(id);
        foto.setOrdem(ordem);
        return fotoEnsaioRepository.save(foto);
    }

    private void deletarArquivo(String caminho) {
        try {
            Files.deleteIfExists(Path.of(caminho));
        } catch (IOException ignored) {}
    }
}
