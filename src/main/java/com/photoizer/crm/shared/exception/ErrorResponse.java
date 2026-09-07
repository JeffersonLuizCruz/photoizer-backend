package com.photoizer.crm.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * PATTERN: Error Response Record
 *
 * Contrato padronizado para respostas de erro da API.
 * Inclui `code` para identificação programática de erros de negócio
 * (anti-correlação frontend/backend) e `timestamp` em UTC para
 * consistência entre nós distribuídos.
 *
 * @JsonInclude(NON_NULL) omite campos nulos (fieldErrors, code)
 * para respostas de erro genéricas.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    int status,
    String error,
    String message,
    String code,
    OffsetDateTime timestamp,
    List<FieldError> fieldErrors
) {
    public ErrorResponse(int status, String error, String message) {
        this(status, error, message, null, OffsetDateTime.now(ZoneOffset.UTC), null);
    }

    public ErrorResponse(int status, String error, String message, String code) {
        this(status, error, message, code, OffsetDateTime.now(ZoneOffset.UTC), null);
    }

    public ErrorResponse(int status, String error, String message, List<FieldError> fieldErrors) {
        this(status, error, message, null, OffsetDateTime.now(ZoneOffset.UTC), fieldErrors);
    }

    public record FieldError(String field, String message) {}
}
