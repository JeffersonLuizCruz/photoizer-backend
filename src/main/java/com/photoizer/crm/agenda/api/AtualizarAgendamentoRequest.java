package com.photoizer.crm.agenda.api;

import com.photoizer.crm.shared.model.TipoRepasse;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AtualizarAgendamentoRequest(
    @NotNull UUID pacoteId,
    @NotNull @Future LocalDateTime dataHoraEnsaio,
    @NotBlank String localEnsaio,
    String enderecoCompleto,
    UUID editorId,
    UUID fotografoId,
    java.math.BigDecimal custoDeslocamento,
    Boolean repassarDeslocamento,
    Boolean autorizaUsoImagem,
    String observacoes,
    List<FotografoRepasse> fotografos
) {
    public record FotografoRepasse(UUID fotografoId, java.math.BigDecimal valorRepassar,
                                   TipoRepasse tipoValor, java.math.BigDecimal percentual) {
        public FotografoRepasse(UUID fotografoId, java.math.BigDecimal valorRepassar) {
            this(fotografoId, valorRepassar, TipoRepasse.FIXO, null);
        }
    }
}
