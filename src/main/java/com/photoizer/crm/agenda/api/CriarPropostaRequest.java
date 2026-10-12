package com.photoizer.crm.agenda.api;

import com.photoizer.crm.shared.model.TipoRepasse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Payload de criação de proposta/pré-reserva a partir do calendário.
 * Não inclui dados do cliente nem comprovante: ambos chegam pelo link público.
 */
@Schema(name = "CriarPropostaRequest",
    description = "Dados para criar uma proposta/pré-reserva (não ocupa a agenda até a aprovação).")
public record CriarPropostaRequest(
    @Schema(description = "ID do pacote contratado") @NotNull UUID pacoteId,
    @Schema(description = "ID do editor responsável", nullable = true) UUID editorId,
    @Schema(description = "ID do fotógrafo responsável") @NotNull UUID fotografoId,
    @Schema(description = "Data/hora do ensaio (ISO-8601)") @NotNull LocalDateTime dataHoraEnsaio,
    @Schema(description = "Duração em minutos", example = "60") Integer duracaoMinutos,
    @Schema(description = "Local do ensaio", example = "Estúdio Photoizer") @NotBlank String localEnsaio,
    @Schema(description = "Custo de deslocamento", nullable = true) BigDecimal custoDeslocamento,
    @Schema(description = "Repassar deslocamento ao fotógrafo", nullable = true) Boolean repassarDeslocamento,
    @Schema(description = "Cláusulas adicionais do contrato", nullable = true) String clausulasPersonalizadas,
    @Schema(description = "Observações internas", nullable = true) String observacoes,
    @Schema(description = "ID de indicador existente", nullable = true) UUID indicadorId,
    @Schema(description = "Nome do indicador (quando não cadastrado)", nullable = true) String indicadorNome,
    @Schema(description = "Telefone do indicador", nullable = true) String indicadorTelefone,
    @Schema(description = "Parceiros e repasses adicionais", nullable = true) List<FotografoRepasse> fotografos
) {
    @Schema(name = "FotografoRepasse", description = "Repasse de um profissional vinculado à proposta.")
    public record FotografoRepasse(
        @Schema(description = "ID do profissional") UUID fotografoId,
        @Schema(description = "Valor a repassar", nullable = true) BigDecimal valorRepassar,
        @Schema(description = "Tipo do valor (FIXO ou PERCENTUAL)", nullable = true) TipoRepasse tipoValor,
        @Schema(description = "Percentual (quando tipoValor=PERCENTUAL)", nullable = true) BigDecimal percentual
    ) {}
}
