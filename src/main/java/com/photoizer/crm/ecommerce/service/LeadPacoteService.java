package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.api.LeadPacoteRequest;
import com.photoizer.crm.ecommerce.api.LeadPacoteResponse;
import com.photoizer.crm.ecommerce.event.LeadPacoteCriadoEvent;
import com.photoizer.crm.ecommerce.model.LeadPacote;
import com.photoizer.crm.ecommerce.model.StatusLead;
import com.photoizer.crm.ecommerce.repository.LeadPacoteRepository;
import com.photoizer.crm.pacote.exception.PacoteInativoException;
import com.photoizer.crm.pacote.service.PacoteQueryService;
import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Captação de leads do checkout público de pacotes.
 *
 * <p>É um lead comercial, não um pedido: não reserva agenda nem cobra nada. O
 * time comercial entra em contato e move o status manualmente.
 */
@Service
@Transactional
public class LeadPacoteService {

    private static final Logger log = LoggerFactory.getLogger(LeadPacoteService.class);

    private final LeadPacoteRepository leadRepository;
    private final PacoteQueryService pacoteQueryService;
    private final ApplicationEventPublisher eventPublisher;

    public LeadPacoteService(LeadPacoteRepository leadRepository,
                             PacoteQueryService pacoteQueryService,
                             ApplicationEventPublisher eventPublisher) {
        this.leadRepository = leadRepository;
        this.pacoteQueryService = pacoteQueryService;
        this.eventPublisher = eventPublisher;
    }

    public LeadPacote criar(LeadPacoteRequest request) {
        var pacote = pacoteQueryService.buscarEntityPorId(request.pacoteId());
        if (!Boolean.TRUE.equals(pacote.getAtivo())) {
            throw new PacoteInativoException(request.pacoteId());
        }

        var lead = LeadPacote.builder()
            .pacoteId(pacote.getId())
            .pacoteNome(pacote.getNome())
            .valorBase(pacote.getValorBase())
            .precoFotoExtra(pacote.getPrecoFotoExtra())
            .nome(request.nome().strip())
            .email(request.email() == null || request.email().isBlank() ? null : request.email().strip())
            .telefone(request.telefone().strip())
            .opcaoEntrega(request.opcaoEntrega())
            .formaPagamento(request.formaPagamento())
            .quantidadeExtras(request.quantidadeExtras() == null ? 0 : request.quantidadeExtras())
            .observacao(request.observacao() == null || request.observacao().isBlank()
                ? null : request.observacao().strip())
            .status(StatusLead.NOVO)
            .build();
        lead = leadRepository.save(lead);

        eventPublisher.publishEvent(new LeadPacoteCriadoEvent(
            lead.getId(), lead.getPacoteNome(), lead.getNome()));

        log.atInfo()
            .addKeyValue("event_action", "lead_pacote.criado")
            .addKeyValue("lead_id", lead.getId())
            .addKeyValue("pacote_id", lead.getPacoteId())
            .addKeyValue("quantidade_extras", lead.getQuantidadeExtras())
            .log("Lead de pacote recebido pelo checkout público");
        return lead;
    }

    @Transactional(readOnly = true)
    public Page<LeadPacote> listar(StatusLead status, int page, int perPage) {
        var pageable = PageRequest.of(
            Math.max(page, 1) - 1,
            Math.min(Math.max(perPage, 1), 100),
            Sort.by(Sort.Direction.DESC, "auditInfo.createdAt"));
        return status == null
            ? leadRepository.findAll(pageable)
            : leadRepository.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public long contarNovos() {
        return leadRepository.countByStatus(StatusLead.NOVO);
    }

    public LeadPacoteResponse atualizarStatus(UUID id, StatusLead status) {
        var lead = leadRepository.findById(id)
            .orElseThrow(() -> new NotFoundException(
                ErrorCode.LEAD_NAO_ENCONTRADO, "Lead não encontrado: " + id));
        lead.setStatus(status);
        lead = leadRepository.save(lead);
        log.atInfo()
            .addKeyValue("event_action", "lead_pacote.status_alterado")
            .addKeyValue("lead_id", lead.getId())
            .addKeyValue("status", status)
            .log("Status do lead atualizado");
        return LeadPacoteResponse.of(lead);
    }
}
