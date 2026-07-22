package com.photoizer.crm.ecommerce.api;

import java.util.List;
import java.util.UUID;

public record CheckoutRequest(
    List<UUID> fotoIds
) {}
