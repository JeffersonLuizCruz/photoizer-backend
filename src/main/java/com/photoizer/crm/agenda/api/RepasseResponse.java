package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.RepasseStatus;
import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(name = "RepasseResponse", description = "Repasse pendente/pago de um fotógrafo.")
public record RepasseResponse(
    @Schema(description = "ID do repasse") UUID id,
    @Schema(description = "Metadados de auditoria") AuditInfoResponse auditInfo,
    @Schema(description = "Resumo do agendamento") AgendamentoResumo agendamento,
    @Schema(description = "Resumo do fotógrafo") FotografoResumo fotografo,
    @Schema(description = "Valor a repassar") BigDecimal valorRepassar,
    @Schema(description = "Status do repasse") RepasseStatus status,
    @Schema(description = "Tipo do valor (FIXO/PERCENTUAL)") String tipoValor,
    @Schema(description = "Percentual") BigDecimal percentual,
    @Schema(description = "Papel do parceiro") String papelParceiro,
    @Schema(description = "Data de pagamento") LocalDateTime dataPagamento
) {
    @Schema(name = "AuditInfoResponse", description = "Datas de auditoria.")
    public record AuditInfoResponse(
        String createdAt,
        String updatedAt
    ) {}

    @Schema(name = "RepasseAgendamentoResumo", description = "Resumo do agendamento do repasse.")
    public record AgendamentoResumo(
        UUID id,
        ClienteResumo cliente,
        PacoteResumo pacote,
        LocalDateTime dataHoraEnsaio,
        String status
    ) {}

    @Schema(name = "RepasseClienteResumo", description = "Resumo do cliente.")
    public record ClienteResumo(
        UUID id,
        String nome
    ) {}

    @Schema(name = "RepassePacoteResumo", description = "Resumo do pacote.")
    public record PacoteResumo(
        UUID id,
        String nome
    ) {}

    @Schema(name = "RepasseFotografoResumo", description = "Resumo do fotógrafo.")
    public record FotografoResumo(
        UUID id,
        String nome,
        String email,
        String telefone,
        String papel,
        boolean ativo
    ) {}
}
