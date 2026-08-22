package com.photoizer.crm.edicao.service;

import com.photoizer.crm.edicao.api.EdicaoMapper;
import com.photoizer.crm.edicao.api.FotoEdicaoResponse;
import com.photoizer.crm.edicao.exception.EdicaoNaoEncontradaException;
import com.photoizer.crm.edicao.exception.FotoEdicaoNaoEncontradaException;
import com.photoizer.crm.edicao.exception.FotoSemRawException;
import com.photoizer.crm.edicao.exception.StatusEdicaoInvalidoException;
import com.photoizer.crm.edicao.model.StatusEdicao;
import com.photoizer.crm.edicao.model.StatusFotoEdicao;
import com.photoizer.crm.edicao.repository.EdicaoRepository;
import com.photoizer.crm.edicao.repository.FotoEdicaoRepository;
import com.photoizer.crm.shared.storage.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Serviço responsável pelo upload de fotos editadas.
 * Extraído de EdicaoService para decompor a god class.
 */
@Service
@Transactional
public class EdicaoUploadEditadasService {

    private final EdicaoRepository edicaoRepository;
    private final FotoEdicaoRepository fotoEdicaoRepository;
    private final FileStorageService fileStorageService;

    public EdicaoUploadEditadasService(EdicaoRepository edicaoRepository,
                                       FotoEdicaoRepository fotoEdicaoRepository,
                                       FileStorageService fileStorageService) {
        this.edicaoRepository = edicaoRepository;
        this.fotoEdicaoRepository = fotoEdicaoRepository;
        this.fileStorageService = fileStorageService;
    }

    public List<FotoEdicaoResponse> uploadEditadas(UUID agendamentoId,
                                                    List<MultipartFile> arquivos,
                                                    EdicaoMapper edicaoMapper) {
        var edicao = edicaoRepository.findByAgendamentoId(agendamentoId)
            .orElseThrow(() -> new EdicaoNaoEncontradaException("Processo de edição não encontrado. Envie as fotos RAW primeiro."));

        if (edicao.getStatus() == StatusEdicao.AGUARDANDO_RAW) {
            throw new StatusEdicaoInvalidoException("Aguardando envio das fotos RAW pelo fotógrafo.");
        }

        var fotosRaw = fotoEdicaoRepository.findByEdicaoIdAndStatus(edicao.getId(), StatusFotoEdicao.RAW);

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
                throw new FotoSemRawException(
                    "O arquivo \"" + nomeArquivo + "\" não possui uma foto RAW correspondente. " +
                    "Verifique se o nome do arquivo editado é idêntico ao nome original."
                );
            }
        }

        edicao.setStatus(StatusEdicao.EM_EDICAO);
        edicaoRepository.save(edicao);

        return fotoEdicaoRepository.findByEdicaoIdOrderByOrdemAsc(edicao.getId()).stream()
            .map(edicaoMapper::toResponse)
            .toList();
    }
}
