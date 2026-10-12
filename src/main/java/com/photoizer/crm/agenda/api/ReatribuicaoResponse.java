package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.ReatribuicaoAgendamento;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(name = "ReatribuicaoResponse", description = "Registro de transferência de fotógrafo de um ensaio.")
public record ReatribuicaoResponse(
    @Schema(description = "ID do registro") UUID id,
    @Schema(description = "ID do fotógrafo anterior") UUID fotografoAnteriorId,
    @Schema(description = "Nome do fotógrafo anterior") String fotografoAnteriorNome,
    @Schema(description = "ID do novo fotógrafo") UUID novoFotografoId,
    @Schema(description = "Nome do novo fotógrafo") String novoFotografoNome,
    @Schema(description = "Nome de quem solicitou") String solicitanteNome,
    @Schema(description = "Motivo") String motivo,
    @Schema(description = "Data da transferência") LocalDateTime createdAt
) {
    public static ReatribuicaoResponse of(ReatribuicaoAgendamento r) {
        return new ReatribuicaoResponse(
            r.getId(),
            r.getFotografoAnterior() != null ? r.getFotografoAnterior().getId() : null,
            r.getFotografoAnterior() != null ? r.getFotografoAnterior().getNome() : null,
            r.getNovoFotografo() != null ? r.getNovoFotografo().getId() : null,
            r.getNovoFotografo() != null ? r.getNovoFotografo().getNome() : null,
            r.getSolicitante() != null ? r.getSolicitante().getNome() : null,
            r.getMotivo(),
            r.getAuditInfo() != null ? r.getAuditInfo().getCreatedAt() : null
        );
    }
}
