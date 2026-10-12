package com.photoizer.crm.agenda.api;

import com.photoizer.crm.shared.model.TipoRepasse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(name = "AtualizarAgendamentoRequest", description = "Dados para atualização de um agendamento.")
public record AtualizarAgendamentoRequest(
    @Schema(description = "ID do pacote") @NotNull UUID pacoteId,
    @Schema(description = "Data/hora do ensaio (futura, ISO-8601)")
    @NotNull @Future LocalDateTime dataHoraEnsaio,
    @Schema(description = "Local do ensaio") @NotBlank String localEnsaio,
    @Schema(description = "ID do editor", nullable = true) UUID editorId,
    @Schema(description = "ID do fotógrafo", nullable = true) UUID fotografoId,
    @Schema(description = "Custo de deslocamento", nullable = true) java.math.BigDecimal custoDeslocamento,
    @Schema(description = "Repassar deslocamento", nullable = true) Boolean repassarDeslocamento,
    @Schema(description = "Autorização de uso de imagem", nullable = true) Boolean autorizaUsoImagem,
    @Schema(description = "Observações", nullable = true) String observacoes,
    @Schema(description = "Parceiros e repasses", nullable = true) List<FotografoRepasse> fotografos
) {
    @Schema(name = "FotografoRepasseAtualizacao", description = "Repasse de profissional no agendamento.")
    public record FotografoRepasse(
        @Schema(description = "ID do profissional") UUID fotografoId,
        @Schema(description = "Valor a repassar", nullable = true) java.math.BigDecimal valorRepassar,
        @Schema(description = "Tipo do valor (FIXO/PERCENTUAL)", nullable = true) TipoRepasse tipoValor,
        @Schema(description = "Percentual", nullable = true) java.math.BigDecimal percentual) {
        public FotografoRepasse(UUID fotografoId, java.math.BigDecimal valorRepassar) {
            this(fotografoId, valorRepassar, TipoRepasse.FIXO, null);
        }
    }
}
