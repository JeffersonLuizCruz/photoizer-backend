package com.photoizer.crm.agenda.api;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AtualizarAgendamentoRequest(
    @NotNull UUID pacoteId,
    @NotNull @Future LocalDateTime dataHoraEnsaio,
    @NotBlank String localEnsaio,
    String enderecoCompleto,
    UUID editorId,
    @NotNull @PositiveOrZero BigDecimal taxaDeslocamento,
    Boolean autorizaUsoImagem,
    String observacoes
) {}
