package com.photoizer.crm.foto.api;

import java.time.LocalDate;
import java.util.List;

public record FotoMetadataRequest(
    String titulo,
    String descricao,
    List<String> tags,
    String categoria,
    LocalDate dataSessao,
    Boolean destaque
) {}
