package com.photoizer.crm.edicao.service;

import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.edicao.api.FotoEdicaoResponse;
import com.photoizer.crm.edicao.event.RawEnviadosEvent;
import com.photoizer.crm.edicao.exception.EdicaoNaoEncontradaException;
import com.photoizer.crm.edicao.exception.StatusEdicaoInvalidoException;
import com.photoizer.crm.edicao.model.Edicao;
import com.photoizer.crm.edicao.model.FotoEdicao;
import com.photoizer.crm.edicao.model.StatusEdicao;
import com.photoizer.crm.edicao.model.StatusFotoEdicao;
import com.photoizer.crm.edicao.repository.EdicaoRepository;
import com.photoizer.crm.edicao.repository.FotoEdicaoRepository;
import com.photoizer.crm.shared.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Serviço responsável pelo upload de fotos RAW.
 * Extraído de EdicaoService para decompor a god class.
 */
@Service
@Transactional
public class RawUploadService {

    private static final Logger log = LoggerFactory.getLogger(RawUploadService.class);

    private final EdicaoRepository edicaoRepository;
    private final FotoEdicaoRepository fotoEdicaoRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;

    public RawUploadService(EdicaoRepository edicaoRepository,
                            FotoEdicaoRepository fotoEdicaoRepository,
                            AgendamentoRepository agendamentoRepository,
                            FileStorageService fileStorageService,
                            ApplicationEventPublisher eventPublisher,
                            UserRepository userRepository) {
        this.edicaoRepository = edicaoRepository;
        this.fotoEdicaoRepository = fotoEdicaoRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.fileStorageService = fileStorageService;
        this.eventPublisher = eventPublisher;
        this.userRepository = userRepository;
    }

    public java.util.List<FotoEdicaoResponse> uploadRaw(UUID agendamentoId,
                                                         java.util.List<MultipartFile> arquivos,
                                                         com.photoizer.crm.edicao.api.EdicaoMapper edicaoMapper) {
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
        if (edicao.getFotografo() == null) {
            edicao.setFotografo(getCurrentUser());
        }
        edicaoRepository.save(edicao);

        if (agendamento.getStatus() == StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL) {
            agendamento.transicionarPara(StatusAgendamento.EM_EDICAO);
            agendamentoRepository.save(agendamento);
        }

        eventPublisher.publishEvent(new RawEnviadosEvent(agendamentoId, fotos.size()));

        return fotos.stream().map(edicaoMapper::toResponse).toList();
    }

    private User getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return null;
        }
        return userRepository.findById(UUID.fromString(auth.getName())).orElse(null);
    }
}
