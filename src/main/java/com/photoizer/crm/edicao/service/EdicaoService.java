package com.photoizer.crm.edicao.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.edicao.api.EdicaoResponse;
import com.photoizer.crm.edicao.api.FotoEdicaoResponse;
import com.photoizer.crm.edicao.event.EdicaoConcluidaEvent;
import com.photoizer.crm.edicao.event.FotosPublicadasEvent;
import com.photoizer.crm.edicao.event.RawEnviadosEvent;
import com.photoizer.crm.edicao.exception.EdicaoNaoEncontradaException;
import com.photoizer.crm.edicao.exception.FotoEdicaoNaoEncontradaException;
import com.photoizer.crm.edicao.exception.StatusEdicaoInvalidoException;
import com.photoizer.crm.edicao.model.Edicao;
import com.photoizer.crm.edicao.model.FotoEdicao;
import com.photoizer.crm.edicao.model.StatusEdicao;
import com.photoizer.crm.edicao.model.StatusFotoEdicao;
import com.photoizer.crm.edicao.repository.EdicaoRepository;
import com.photoizer.crm.edicao.repository.FotoEdicaoRepository;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import com.photoizer.crm.foto.service.ImageProcessingService;
import com.photoizer.crm.shared.storage.FileStorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Transactional
public class EdicaoService {

    private static final String TEXTO_MARCA_DAGUA = "© Photoizer Studio";
    private static final float OPACIDADE_MARCA = 0.15f;

    private final EdicaoRepository edicaoRepository;
    private final FotoEdicaoRepository fotoEdicaoRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final FileStorageService fileStorageService;
    private final ImageProcessingService imageProcessingService;
    private final ApplicationEventPublisher eventPublisher;

    public EdicaoService(EdicaoRepository edicaoRepository,
                         FotoEdicaoRepository fotoEdicaoRepository,
                         AgendamentoRepository agendamentoRepository,
                         FotoEnsaioRepository fotoEnsaioRepository,
                         FileStorageService fileStorageService,
                         ImageProcessingService imageProcessingService,
                         ApplicationEventPublisher eventPublisher) {
        this.edicaoRepository = edicaoRepository;
        this.fotoEdicaoRepository = fotoEdicaoRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.fileStorageService = fileStorageService;
        this.imageProcessingService = imageProcessingService;
        this.eventPublisher = eventPublisher;
    }

    public EdicaoResponse obterStatus(UUID agendamentoId) {
        var edicao = edicaoRepository.findByAgendamentoId(agendamentoId)
            .orElse(null);
        if (edicao == null) {
            return null;
        }
        var totalRaw = fotoEdicaoRepository.countByEdicaoIdAndStatus(edicao.getId(), StatusFotoEdicao.RAW);
        var totalEditadas = fotoEdicaoRepository.countByEdicaoIdAndStatus(edicao.getId(), StatusFotoEdicao.EDITADO);
        return EdicaoResponse.of(edicao, totalRaw, totalEditadas);
    }

    @Transactional(readOnly = true)
    public List<EdicaoResponse> listarTodos() {
        var edicoes = edicaoRepository.findAllByOrderByUpdatedAtDesc();
        return edicoes.stream()
            .map(e -> {
                var totalRaw = fotoEdicaoRepository.countByEdicaoIdAndStatus(e.getId(), StatusFotoEdicao.RAW);
                var totalEditadas = fotoEdicaoRepository.countByEdicaoIdAndStatus(e.getId(), StatusFotoEdicao.EDITADO);
                return EdicaoResponse.of(e, totalRaw, totalEditadas);
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public List<EdicaoResponse> listarPorStatus(StatusEdicao status) {
        var edicoes = edicaoRepository.findByStatusOrderByUpdatedAtDesc(status);
        return edicoes.stream()
            .map(e -> {
                var totalRaw = fotoEdicaoRepository.countByEdicaoIdAndStatus(e.getId(), StatusFotoEdicao.RAW);
                var totalEditadas = fotoEdicaoRepository.countByEdicaoIdAndStatus(e.getId(), StatusFotoEdicao.EDITADO);
                return EdicaoResponse.of(e, totalRaw, totalEditadas);
            })
            .toList();
    }

    public List<FotoEdicaoResponse> listarFotos(UUID agendamentoId) {
        var edicao = edicaoRepository.findByAgendamentoId(agendamentoId)
            .orElseThrow(() -> new EdicaoNaoEncontradaException("Processo de edição não encontrado para este ensaio"));
        return fotoEdicaoRepository.findByEdicaoIdOrderByOrdemAsc(edicao.getId()).stream()
            .map(FotoEdicaoResponse::of)
            .toList();
    }

    public FotoEdicao buscarFoto(UUID fotoId) {
        return fotoEdicaoRepository.findById(fotoId)
            .orElseThrow(() -> new FotoEdicaoNaoEncontradaException("Foto não encontrada: " + fotoId));
    }

    @Transactional
    public void deletarFoto(UUID fotoId) {
        var foto = buscarFoto(fotoId);
        fileStorageService.deletar(foto.getRawPath());
        if (foto.getEditedPath() != null) {
            fileStorageService.deletar(foto.getEditedPath());
        }
        fotoEdicaoRepository.deleteById(fotoId);
    }

    public List<FotoEdicaoResponse> uploadRaw(UUID agendamentoId, List<MultipartFile> arquivos) {
        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new EdicaoNaoEncontradaException("Agendamento não encontrado: " + agendamentoId));

        if (agendamento.getStatus() != StatusAgendamento.EM_EDICAO
            && agendamento.getStatus() != StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL) {
            throw new StatusEdicaoInvalidoException(
                "O ensaio precisa estar como EM_EDICAO ou AGUARDANDO_PAGAMENTO_FINAL para receber fotos RAW. Status atual: " + agendamento.getStatus()
            );
        }

        var edicao = edicaoRepository.findByAgendamentoId(agendamentoId)
            .orElseGet(() -> edicaoRepository.save(Edicao.builder()
                .agendamentoId(agendamentoId)
                .status(StatusEdicao.AGUARDANDO_RAW)
                .build()));

        var fotos = new ArrayList<FotoEdicao>();
        var count = fotoEdicaoRepository.countByEdicaoId(edicao.getId());

        for (int i = 0; i < arquivos.size(); i++) {
            var arquivo = arquivos.get(i);
            var rawPath = fileStorageService.salvarEmSubdiretorio(arquivo, agendamentoId, "raw");

            var foto = FotoEdicao.builder()
                .edicaoId(edicao.getId())
                .rawPath(rawPath)
                .rawFileName(arquivo.getOriginalFilename())
                .status(StatusFotoEdicao.RAW)
                .ordem(count + i)
                .build();

            fotos.add(fotoEdicaoRepository.save(foto));
        }

        edicao.setStatus(StatusEdicao.RAW_ENVIADOS);
        edicao.setDataEnvioRaw(LocalDateTime.now());
        edicaoRepository.save(edicao);

        if (agendamento.getStatus() == StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL) {
            agendamento.setStatus(StatusAgendamento.EM_EDICAO);
            agendamentoRepository.save(agendamento);
        }

        eventPublisher.publishEvent(new RawEnviadosEvent(agendamentoId, fotos.size()));

        return fotos.stream().map(FotoEdicaoResponse::of).toList();
    }

    public List<FotoEdicaoResponse> uploadEditadas(UUID agendamentoId, List<MultipartFile> arquivos) {
        var edicao = edicaoRepository.findByAgendamentoId(agendamentoId)
            .orElseThrow(() -> new EdicaoNaoEncontradaException("Processo de edição não encontrado. Envie as fotos RAW primeiro."));

        if (edicao.getStatus() == StatusEdicao.AGUARDANDO_RAW) {
            throw new StatusEdicaoInvalidoException("Aguardando envio das fotos RAW pelo fotógrafo.");
        }

        var fotosRaw = fotoEdicaoRepository.findByEdicaoIdAndStatus(edicao.getId(), StatusFotoEdicao.RAW);
        var ordemAtual = fotoEdicaoRepository.countByEdicaoId(edicao.getId());

        for (var arquivo : arquivos) {
            var editedPath = fileStorageService.salvarEmSubdiretorio(arquivo, agendamentoId, "edit");
            var nomeArquivo = arquivo.getOriginalFilename();

            var fotoExistente = nomeArquivo != null
                ? fotosRaw.stream()
                    .filter(f -> nomeArquivo.equals(f.getRawFileName()))
                    .findFirst()
                    .orElse(null)
                : null;

            if (fotoExistente != null) {
                fotoExistente.setEditedPath(editedPath);
                fotoExistente.setEditedFileName(nomeArquivo);
                fotoExistente.setStatus(StatusFotoEdicao.EDITADO);
                fotoEdicaoRepository.save(fotoExistente);
            } else {
                var novaFoto = FotoEdicao.builder()
                    .edicaoId(edicao.getId())
                    .rawPath("")
                    .rawFileName(nomeArquivo != null ? nomeArquivo : "")
                    .editedPath(editedPath)
                    .editedFileName(nomeArquivo)
                    .status(StatusFotoEdicao.EDITADO)
                    .ordem((int) ordemAtual)
                    .build();
                fotoEdicaoRepository.save(novaFoto);
                ordemAtual++;
            }
        }

        edicao.setStatus(StatusEdicao.EM_EDICAO);
        edicaoRepository.save(edicao);

        return fotoEdicaoRepository.findByEdicaoIdOrderByOrdemAsc(edicao.getId()).stream()
            .map(FotoEdicaoResponse::of)
            .toList();
    }

    public EdicaoResponse concluirEdicao(UUID agendamentoId) {
        var edicao = edicaoRepository.findByAgendamentoId(agendamentoId)
            .orElseThrow(() -> new EdicaoNaoEncontradaException("Processo de edição não encontrado"));

        var totalRaw = fotoEdicaoRepository.countByEdicaoIdAndStatus(edicao.getId(), StatusFotoEdicao.RAW);
        var totalEditadas = fotoEdicaoRepository.countByEdicaoIdAndStatus(edicao.getId(), StatusFotoEdicao.EDITADO);

        if (totalRaw > 0 && totalEditadas == 0) {
            throw new StatusEdicaoInvalidoException("Nenhuma foto editada foi enviada ainda.");
        }

        edicao.setStatus(StatusEdicao.EDICAO_CONCLUIDA);
        edicao.setDataEnvioEditado(LocalDateTime.now());
        edicao = edicaoRepository.save(edicao);

        eventPublisher.publishEvent(new EdicaoConcluidaEvent(agendamentoId));

        var totalRawFinal = fotoEdicaoRepository.countByEdicaoIdAndStatus(edicao.getId(), StatusFotoEdicao.RAW);
        var totalEditadasFinal = fotoEdicaoRepository.countByEdicaoIdAndStatus(edicao.getId(), StatusFotoEdicao.EDITADO);
        return EdicaoResponse.of(edicao, totalRawFinal, totalEditadasFinal);
    }

    public List<FotoEdicaoResponse> publicarNoEcommerce(UUID agendamentoId) {
        var edicao = edicaoRepository.findByAgendamentoId(agendamentoId)
            .orElseThrow(() -> new EdicaoNaoEncontradaException("Processo de edição não encontrado"));

        if (edicao.getStatus() != StatusEdicao.EDICAO_CONCLUIDA) {
            throw new StatusEdicaoInvalidoException(
                "A edição precisa estar concluída para publicar no ecommerce. Status atual: " + edicao.getStatus()
            );
        }

        var fotosEditadas = fotoEdicaoRepository.findByEdicaoIdAndStatus(edicao.getId(), StatusFotoEdicao.EDITADO);

        if (fotosEditadas.isEmpty()) {
            throw new FotoEdicaoNaoEncontradaException("Nenhuma foto editada encontrada para publicar.");
        }

        var count = fotoEnsaioRepository.countByAgendamentoId(agendamentoId);

        for (int i = 0; i < fotosEditadas.size(); i++) {
            var fotoEdicao = fotosEditadas.get(i);
            var editedPath = Path.of(fotoEdicao.getEditedPath());
            var targetDir = editedPath.getParent();

            String watermarkedPath;
            String thumbPath;
            try {
                var wm = imageProcessingService.aplicarMarcaDagua(editedPath, targetDir, TEXTO_MARCA_DAGUA, OPACIDADE_MARCA);
                watermarkedPath = wm.toString();
            } catch (Exception e) {
                watermarkedPath = fotoEdicao.getEditedPath();
            }
            try {
                var thumb = imageProcessingService.gerarThumbnail(editedPath, targetDir);
                thumbPath = thumb.toString();
            } catch (Exception e) {
                thumbPath = fotoEdicao.getEditedPath();
            }

            var fotoEnsaio = FotoEnsaio.builder()
                .agendamentoId(agendamentoId)
                .fileName(fotoEdicao.getEditedFileName() != null ? fotoEdicao.getEditedFileName() : fotoEdicao.getRawFileName())
                .originalPath(fotoEdicao.getEditedPath())
                .watermarkedPath(watermarkedPath)
                .thumbPath(thumbPath)
                .ordem(count + i)
                .status(StatusFoto.PUBLICADA)
                .selecionadaPacote(false)
                .build();

            fotoEnsaioRepository.save(fotoEnsaio);
        }

        eventPublisher.publishEvent(new FotosPublicadasEvent(agendamentoId, fotosEditadas.size()));

        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new EdicaoNaoEncontradaException("Agendamento não encontrado: " + agendamentoId));
        agendamento.setStatus(StatusAgendamento.SELECAO_DAS_FOTOS);
        agendamentoRepository.save(agendamento);

        return fotoEdicaoRepository.findByEdicaoIdOrderByOrdemAsc(edicao.getId()).stream()
            .map(FotoEdicaoResponse::of)
            .toList();
    }

    public EdicaoResponse atualizarObservacoes(UUID agendamentoId, String observacoes) {
        var edicao = edicaoRepository.findByAgendamentoId(agendamentoId)
            .orElseThrow(() -> new EdicaoNaoEncontradaException("Processo de edição não encontrado"));
        edicao.setObservacoes(observacoes);
        edicao = edicaoRepository.save(edicao);
        var totalRaw = fotoEdicaoRepository.countByEdicaoIdAndStatus(edicao.getId(), StatusFotoEdicao.RAW);
        var totalEditadas = fotoEdicaoRepository.countByEdicaoIdAndStatus(edicao.getId(), StatusFotoEdicao.EDITADO);
        return EdicaoResponse.of(edicao, totalRaw, totalEditadas);
    }

    @SuppressWarnings("unchecked")
    public List<FotoEdicaoResponse> reordenarFotos(List<Map<String, Object>> fotos) {
        for (var item : fotos) {
            var id = UUID.fromString(item.get("id").toString());
            var ordem = ((Number) item.get("ordem")).intValue();
            fotoEdicaoRepository.findById(id).ifPresent(foto -> {
                foto.setOrdem(ordem);
                fotoEdicaoRepository.save(foto);
            });
        }
        var primeira = fotoEdicaoRepository.findById(
            UUID.fromString(fotos.getFirst().get("id").toString())
        ).orElseThrow(() -> new FotoEdicaoNaoEncontradaException("Foto não encontrada"));
        return fotoEdicaoRepository.findByEdicaoIdOrderByOrdemAsc(primeira.getEdicaoId()).stream()
            .map(FotoEdicaoResponse::of)
            .toList();
    }

    public Path gerarZipRaw(UUID agendamentoId) throws IOException {
        var edicao = edicaoRepository.findByAgendamentoId(agendamentoId)
            .orElseThrow(() -> new EdicaoNaoEncontradaException("Processo de edição não encontrado"));

        var fotos = fotoEdicaoRepository.findByEdicaoIdOrderByOrdemAsc(edicao.getId());

        var zipDir = fileStorageService.getUploadDir().resolve("temp");
        Files.createDirectories(zipDir);
        limparZipsAntigos(zipDir, "raw_" + agendamentoId);
        var zipPath = zipDir.resolve("raw_" + agendamentoId + "_" + UUID.randomUUID() + ".zip");

        try (var zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            for (var foto : fotos) {
                var filePath = Path.of(foto.getRawPath());
                if (Files.exists(filePath)) {
                    zos.putNextEntry(new ZipEntry(foto.getRawFileName()));
                    Files.copy(filePath, zos);
                    zos.closeEntry();
                }
            }
        }

        return zipPath;
    }

    public Path gerarZipEditadas(UUID agendamentoId) throws IOException {
        var edicao = edicaoRepository.findByAgendamentoId(agendamentoId)
            .orElseThrow(() -> new EdicaoNaoEncontradaException("Processo de edição não encontrado"));

        var fotos = fotoEdicaoRepository.findByEdicaoIdOrderByOrdemAsc(edicao.getId());

        var zipDir = fileStorageService.getUploadDir().resolve("temp");
        Files.createDirectories(zipDir);
        limparZipsAntigos(zipDir, "editadas_" + agendamentoId);
        var zipPath = zipDir.resolve("editadas_" + agendamentoId + "_" + UUID.randomUUID() + ".zip");

        try (var zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            for (var foto : fotos) {
                var caminho = foto.getEditedPath() != null ? foto.getEditedPath() : foto.getRawPath();
                var nome = foto.getEditedFileName() != null ? foto.getEditedFileName() : foto.getRawFileName();
                var filePath = Path.of(caminho);
                if (Files.exists(filePath)) {
                    zos.putNextEntry(new ZipEntry(nome));
                    Files.copy(filePath, zos);
                    zos.closeEntry();
                }
            }
        }

        return zipPath;
    }

    private void limparZipsAntigos(Path diretorio, String prefixo) {
        if (!Files.exists(diretorio)) return;
        try (var files = Files.list(diretorio)) {
            files.filter(f -> f.getFileName().toString().startsWith(prefixo))
                .forEach(f -> {
                    try { Files.deleteIfExists(f); } catch (IOException ignored) {}
                });
        } catch (IOException ignored) {}
    }
}
