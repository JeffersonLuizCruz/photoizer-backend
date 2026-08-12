package com.photoizer.crm.contrato.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    String indicadorTelefone
) {
}