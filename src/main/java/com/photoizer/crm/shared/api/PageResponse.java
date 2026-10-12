package com.photoizer.crm.shared.api;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(name = "PageResponse", description = "Envelope genérico de resposta paginada.")
public record PageResponse<T>(
    @Schema(description = "Itens da página atual") List<T> data,
    @Schema(description = "Total de registros", example = "137") long total,
    @Schema(description = "Página atual (1-based)", example = "1") int page,
    @Schema(description = "Itens por página", example = "10") int perPage,
    @Schema(description = "Total de páginas", example = "14") int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> springPage, int requestedPage) {
        return new PageResponse<>(
            springPage.getContent(),
            springPage.getTotalElements(),
            requestedPage,
            springPage.getSize(),
            springPage.getTotalPages()
        );
    }
}
