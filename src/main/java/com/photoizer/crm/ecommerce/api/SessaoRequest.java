package com.photoizer.crm.ecommerce.api;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.UUID;

public record SessaoRequest(
    @NotBlank String nomeSessao,
    LocalDate dataRealizacao,
    String local,
    String descricao,
    String status,
    UUID clienteId
) {}
