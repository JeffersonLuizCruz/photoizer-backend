package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.StatusSessao;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.UUID;

public record SessaoRequest(
    @NotBlank String nomeSessao,
    LocalDate dataRealizacao,
    String local,
    String descricao,
    StatusSessao status,
    UUID clienteId
) {}
