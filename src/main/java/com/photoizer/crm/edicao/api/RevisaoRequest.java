package com.photoizer.crm.edicao.api;

public record RevisaoRequest(
    Boolean aprovado,
    String comentario
) {
}
