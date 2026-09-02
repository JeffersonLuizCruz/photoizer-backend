package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.RepasseStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO para representação de um repasse de fotógrafo.
 *
 * Design Pattern: Data Transfer Object (DTO) — substitui o retorno
 * direto da entidade AgendamentoFotografo na API, evitando:
 * 1. Vazamento de dados internos do JPA (lazy loading, proxies)
 * 2. Serialização de campos sensíveis ou de navegabilidade
 * 3. Acoplamento entre contrato da API e modelo de persistência
 *
 * NOTA: A estrutura é compatível com a interface AgendamentoFotografo
 * do frontend TypeScript para manter backward compatibility.
 */
public record RepasseResponse(
    UUID id,
    AuditInfoResponse auditInfo,
    AgendamentoResumo agendamento,
    FotografoResumo fotografo,
    BigDecimal valorRepassar,
    RepasseStatus status,
    String tipoValor,
    BigDecimal percentual,
    String papelParceiro,
    LocalDateTime dataPagamento
) {
    public record AuditInfoResponse(
        String createdAt,
        String updatedAt
    ) {}

    public record AgendamentoResumo(
        UUID id,
        ClienteResumo cliente,
        PacoteResumo pacote,
        LocalDateTime dataHoraEnsaio,
        String status
    ) {}

    public record ClienteResumo(
        UUID id,
        String nome
    ) {}

    public record PacoteResumo(
        UUID id,
        String nome
    ) {}

    public record FotografoResumo(
        UUID id,
        String nome,
        String email,
        String telefone,
        String papel,
        boolean ativo
    ) {}
}
