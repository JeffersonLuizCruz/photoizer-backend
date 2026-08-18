package com.photoizer.crm.contrato.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.photoizer.crm.shared.model.TipoRepasse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CriarContratoRequest(
    UUID clienteId,
    @NotNull UUID pacoteId,
    @NotNull LocalDateTime dataHoraEnsaio,
    @Positive Integer duracaoMinutos,
    @NotBlank String localEnsaio,
    String enderecoCompleto,
    UUID editorId,
    BigDecimal custoDeslocamento,
    Boolean repassarDeslocamento,
    String observacoes,
    UUID indicadorId,
    String indicadorNome,
    String indicadorTelefone,
    UUID fotografoId,
    BigDecimal valorRepassarFotografo,
    List<FotografoRepasse> fotografos
) {
    public record FotografoRepasse(
        UUID fotografoId,
        BigDecimal valorRepassar,
        TipoRepasse tipoValor,
        BigDecimal percentual
    ) {}
}