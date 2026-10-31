package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.LeadPacote;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record LeadPacoteResponse(
    UUID id,
    UUID pacoteId,
    String pacoteNome,
    BigDecimal valorBase,
    BigDecimal precoFotoExtra,
    String nome,
    String email,
    String telefone,
    String opcaoEntrega,
    String formaPagamento,
    Integer quantidadeExtras,
    String observacao,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static LeadPacoteResponse of(LeadPacote lead) {
        return new LeadPacoteResponse(
            lead.getId(),
            lead.getPacoteId(),
            lead.getPacoteNome(),
            lead.getValorBase(),
            lead.getPrecoFotoExtra(),
            lead.getNome(),
            lead.getEmail(),
            lead.getTelefone(),
            lead.getOpcaoEntrega(),
            lead.getFormaPagamento(),
            lead.getQuantidadeExtras(),
            lead.getObservacao(),
            lead.getStatus().name(),
            lead.getAuditInfo() != null ? lead.getAuditInfo().getCreatedAt() : null,
            lead.getAuditInfo() != null ? lead.getAuditInfo().getUpdatedAt() : null
        );
    }
}
