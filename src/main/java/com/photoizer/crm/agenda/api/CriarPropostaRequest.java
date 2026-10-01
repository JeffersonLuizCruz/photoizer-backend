package com.photoizer.crm.agenda.api;

import com.photoizer.crm.shared.model.TipoRepasse;
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
public record CriarPropostaRequest(
    @NotNull UUID pacoteId,
    UUID editorId,
    @NotNull UUID fotografoId,
    @NotNull LocalDateTime dataHoraEnsaio,
    Integer duracaoMinutos,
    @NotBlank String localEnsaio,
    BigDecimal custoDeslocamento,
    Boolean repassarDeslocamento,
    String clausulasPersonalizadas,
    String observacoes,
    UUID indicadorId,
    String indicadorNome,
    String indicadorTelefone,
    List<FotografoRepasse> fotografos
) {
    public record FotografoRepasse(
        UUID fotografoId,
        BigDecimal valorRepassar,
        TipoRepasse tipoValor,
        BigDecimal percentual
    ) {}
}
