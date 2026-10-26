package com.photoizer.crm.ecommerce.api;

import java.util.UUID;

/**
 * Define a capa manual da galeria. {@code fotoId = null} remove a capa.
 */
public record DefinirCapaRequest(UUID fotoId) {}
