package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.StatusLead;
import jakarta.validation.constraints.NotNull;

public record AtualizarStatusLeadRequest(
    @NotNull(message = "Informe o status")
    StatusLead status
) {}
