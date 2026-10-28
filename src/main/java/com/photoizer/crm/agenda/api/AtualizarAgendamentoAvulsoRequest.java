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
 * Payload de correção do agendamento avulso. Diferente de
 * {@link AtualizarAgendamentoRequest}, aceita data/hora no passado para
 * regularizar ensaios que já aconteceram.
 */
@Schema(name = "AtualizarAgendamentoAvulsoRequest",
    description = "Dados para corrigir um agendamento avulso. Aceita data passada para "
        + "registros retroativos; nesse caso o conflito de agenda não é revalidado.")
public record AtualizarAgendamentoAvulsoRequest(
    @Schema(description = "ID do pacote") @NotNull UUID pacoteId,
    @Schema(description = "Data/hora do ensaio (ISO-8601). Aceita datas passadas.")
    @NotNull LocalDateTime dataHoraEnsaio,
    @Schema(description = "Local do ensaio") @NotBlank String localEnsaio,
    @Schema(description = "ID do editor", nullable = true) UUID editorId,
    @Schema(description = "ID do fotógrafo", nullable = true) UUID fotografoId,
    @Schema(description = "Custo de deslocamento", nullable = true) BigDecimal custoDeslocamento,
    @Schema(description = "Repassar deslocamento", nullable = true) Boolean repassarDeslocamento,
    @Schema(description = "Autorização de uso de imagem", nullable = true) Boolean autorizaUsoImagem,
    @Schema(description = "Observações", nullable = true) String observacoes,
    @Schema(description = "Parceiros e repasses", nullable = true) List<FotografoRepasse> fotografos
) {
    /** Converte para o payload compartilhado de atualização. */
    public AtualizarAgendamentoRequest toRequest() {
        var repasses = fotografos == null
            ? null
            : fotografos.stream()
                .map(f -> new AtualizarAgendamentoRequest.FotografoRepasse(
                    f.fotografoId(), f.valorRepassar(), f.tipoValor(), f.percentual()))
                .toList();
        return new AtualizarAgendamentoRequest(
            pacoteId,
            dataHoraEnsaio,
            localEnsaio,
            editorId,
            fotografoId,
            custoDeslocamento,
            repassarDeslocamento,
            autorizaUsoImagem,
            observacoes,
            repasses);
    }

    @Schema(name = "FotografoRepasseAvulso", description = "Repasse de profissional no agendamento avulso.")
    public record FotografoRepasse(
        @Schema(description = "ID do profissional") UUID fotografoId,
        @Schema(description = "Valor a repassar", nullable = true) BigDecimal valorRepassar,
        @Schema(description = "Tipo do valor (FIXO/PERCENTUAL)", nullable = true) TipoRepasse tipoValor,
        @Schema(description = "Percentual", nullable = true) BigDecimal percentual) {
        public FotografoRepasse(UUID fotografoId, BigDecimal valorRepassar) {
            this(fotografoId, valorRepassar, TipoRepasse.FIXO, null);
        }
    }
}
