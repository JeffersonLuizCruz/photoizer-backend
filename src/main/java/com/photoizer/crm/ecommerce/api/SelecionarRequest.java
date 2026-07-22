package com.photoizer.crm.ecommerce.api;

import java.util.List;
import java.util.UUID;

public record SelecionarRequest(
    List<UUID> fotoIds,
    boolean selecionada
) {}
