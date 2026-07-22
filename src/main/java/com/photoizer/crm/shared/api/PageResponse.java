package com.photoizer.crm.shared.api;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(
    List<T> data,
    long total,
    int page,
    int perPage,
    int totalPages
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
