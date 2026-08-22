package com.photoizer.crm.edicao.service;

import com.photoizer.crm.edicao.api.EdicaoMapper;
import com.photoizer.crm.edicao.api.EdicaoResponse;
import com.photoizer.crm.edicao.api.FotoEdicaoResponse;
import com.photoizer.crm.edicao.api.ObservacoesRequest;
import com.photoizer.crm.edicao.api.ReordenarFotoRequest;
import com.photoizer.crm.edicao.api.RevisaoRequest;
import com.photoizer.crm.edicao.exception.EdicaoNaoEncontradaException;
import com.photoizer.crm.edicao.exception.FotoEdicaoNaoEncontradaException;
import com.photoizer.crm.edicao.model.StatusEdicao;
import com.photoizer.crm.edicao.model.StatusFotoEdicao;
import com.photoizer.crm.edicao.repository.EdicaoRepository;
import com.photoizer.crm.edicao.repository.FotoEdicaoRepository;
import com.photoizer.crm.edicao.service.PublicacaoService.PublicacaoTipo;
import com.photoizer.crm.shared.storage.FileStorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Orquestrador fino do módulo de edição.
 * Delega responsabilidades para services especializados:
 * - EdicaoQueryService: operações de leitura
 * - RawUploadService: upload de fotos RAW
 * - EdicaoUploadEditadasService: upload de fotos editadas
 * - PublicacaoService: publicação ecommerce/loja
 * - EdicaoRevisaoService: revisão individual
 * - FotoEdicaoProcessor: processamento de imagem (watermark/thumbnail)
 * - EdicaoZipService: geração de ZIPs
 */
@Service
@Transactional
public class EdicaoService {

    private final EdicaoRepository edicaoRepository;
    private final FotoEdicaoRepository fotoEdicaoRepository;
    private final EdicaoQueryService edicaoQueryService;
    private final RawUploadService rawUploadService;
    private final EdicaoUploadEditadasService uploadEditadasService;
    private final PublicacaoService publicacaoService;
    private final EdicaoRevisaoService revisaoService;
    private final EdicaoZipService zipService;
    private final FileStorageService fileStorageService;
    private final EdicaoMapper edicaoMapper;
    private final ApplicationEventPublisher eventPublisher;

    public EdicaoService(EdicaoRepository edicaoRepository,
                         FotoEdicaoRepository fotoEdicaoRepository,
                         EdicaoQueryService edicaoQueryService,
                         RawUploadService rawUploadService,
                         EdicaoUploadEditadasService uploadEditadasService,
                         PublicacaoService publicacaoService,
                         EdicaoRevisaoService revisaoService,
                         EdicaoZipService zipService,
                         FileStorageService fileStorageService,
                         EdicaoMapper edicaoMapper,
                         ApplicationEventPublisher eventPublisher) {
        this.edicaoRepository = edicaoRepository;
        this.fotoEdicaoRepository = fotoEdicaoRepository;
        this.edicaoQueryService = edicaoQueryService;
        this.rawUploadService = rawUploadService;
        this.uploadEditadasService = uploadEditadasService;
        this.publicacaoService = publicacaoService;
        this.revisaoService = revisaoService;
        this.zipService = zipService;
        this.fileStorageService = fileStorageService;
        this.edicaoMapper = edicaoMapper;
        this.eventPublisher = eventPublisher;
    }

    // === Consultas (delega para EdicaoQueryService) ===

    public EdicaoResponse obterStatus(UUID agendamentoId) {
        return edicaoQueryService.obterStatus(agendamentoId);
    }

    public List<EdicaoResponse> listarTodos() {
        return edicaoQueryService.listarTodos();
    }

    public List<EdicaoResponse> listarPorStatus(StatusEdicao status) {
        return edicaoQueryService.listarPorStatus(status);
    }

    public List<FotoEdicaoResponse> listarFotos(UUID agendamentoId) {
        return edicaoQueryService.listarFotos(agendamentoId);
    }

    // === Uploads ===

    public List<FotoEdicaoResponse> uploadRaw(UUID agendamentoId, List<MultipartFile> arquivos) {
        return rawUploadService.uploadRaw(agendamentoId, arquivos, edicaoMapper);
    }

    public List<FotoEdicaoResponse> uploadEditadas(UUID agendamentoId, List<MultipartFile> arquivos) {
        return uploadEditadasService.uploadEditadas(agendamentoId, arquivos, edicaoMapper);
    }

    // === Conclusão ===

    public EdicaoResponse concluirEdicao(UUID agendamentoId) {
        var edicao = edicaoRepository.findByAgendamentoId(agendamentoId)
            .orElseThrow(() -> new EdicaoNaoEncontradaException("Processo de edição não encontrado"));

        var counts = edicaoQueryService.buscarCounts(edicao.getId());

        if (counts.raw() > 0 && counts.editadas() == 0) {
            throw new com.photoizer.crm.edicao.exception.StatusEdicaoInvalidoException(
                "Nenhuma foto editada foi enviada ainda.");
        }

        edicao.setStatus(StatusEdicao.EDICAO_CONCLUIDA);
        edicao.setDataEnvioEditado(LocalDateTime.now());
        edicao = edicaoRepository.save(edicao);

        eventPublisher.publishEvent(
            new com.photoizer.crm.edicao.event.EdicaoConcluidaEvent(agendamentoId));

        var countsFinal = edicaoQueryService.buscarCounts(edicao.getId());
        return edicaoMapper.toResponse(edicao, countsFinal.raw(), countsFinal.editadas());
    }

    // === Publicação ===

    public EdicaoResponse publicarNoEcommerce(UUID agendamentoId) {
        return publicacaoService.publicar(agendamentoId, PublicacaoTipo.ECOMMERCE, edicaoMapper);
    }

    public EdicaoResponse publicarLoja(UUID agendamentoId) {
        return publicacaoService.publicar(agendamentoId, PublicacaoTipo.LOJA, edicaoMapper);
    }

    // === Observações ===

    public EdicaoResponse atualizarObservacoes(UUID agendamentoId, String observacoes) {
        var edicao = edicaoRepository.findByAgendamentoId(agendamentoId)
            .orElseThrow(() -> new EdicaoNaoEncontradaException("Processo de edição não encontrado"));
        edicao.setObservacoes(observacoes);
        edicao = edicaoRepository.save(edicao);
        var counts = edicaoQueryService.buscarCounts(edicao.getId());
        return edicaoMapper.toResponse(edicao, counts.raw(), counts.editadas());
    }

    // === Revisão ===

    public FotoEdicaoResponse revisarFoto(UUID fotoId, RevisaoRequest request) {
        return revisaoService.revisarFoto(fotoId, request, edicaoMapper);
    }

    // === Foto CRUD ===

    public com.photoizer.crm.edicao.model.FotoEdicao buscarFoto(UUID fotoId) {
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

    // === Reordenação ===

    public List<FotoEdicaoResponse> reordenarFotos(List<ReordenarFotoRequest> fotos) {
        for (var item : fotos) {
            fotoEdicaoRepository.findById(item.id()).ifPresent(foto -> {
                foto.setOrdem(item.ordem());
                fotoEdicaoRepository.save(foto);
            });
        }
        var primeira = fotoEdicaoRepository.findById(fotos.getFirst().id())
            .orElseThrow(() -> new FotoEdicaoNaoEncontradaException("Foto não encontrada"));
        return fotoEdicaoRepository.findByEdicaoIdOrderByOrdemAsc(primeira.getEdicaoId()).stream()
            .map(edicaoMapper::toResponse)
            .toList();
    }

    // === ZIPs ===

    public Path gerarZipRaw(UUID agendamentoId) throws IOException {
        return zipService.gerarZipRaw(agendamentoId);
    }

    public Path gerarZipEditadas(UUID agendamentoId) throws IOException {
        return zipService.gerarZipEditadas(agendamentoId);
    }
}
