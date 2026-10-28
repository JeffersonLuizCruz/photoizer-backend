package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.shared.processing.QualidadePreview;
import jakarta.validation.constraints.NotNull;

public record AlterarQualidadePreviewRequest(
    @NotNull(message = "Informe a qualidade da prévia")
    QualidadePreview qualidade
) {}
