package com.photoizer.crm.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.photoizer.crm.shared.logging.LoggingKeys;
import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.MDC;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * PATTERN: Error Response Record
 *
 * Contrato padronizado para respostas de erro da API.
 * Inclui `code` para identificação programática de erros de negócio
 * (anti-correlação frontend/backend), `timestamp` em UTC e `correlationId`
 * (mesmo valor do header {@code X-Correlation-ID}) para suporte/observabilidade.
 *
 * @JsonInclude(NON_NULL) omite campos nulos (fieldErrors, code, correlationId)
 * para respostas de erro genéricas.
 */
@Schema(name = "ErrorResponse", description = "Contrato padronizado de erro da API.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    @Schema(description = "Status HTTP", example = "404")
    int status,
    @Schema(description = "Motivo HTTP (reason phrase)", example = "Not Found")
    String error,
    @Schema(description = "Mensagem legível para o usuário", example = "Cliente não encontrado")
    String message,
    @Schema(description = "Código de negócio (enum ErrorCode) para tratamento programático",
        example = "CLIENTE_NAO_ENCONTRADO", nullable = true)
    String code,
    @Schema(description = "Momento do erro em UTC (ISO-8601)")
    OffsetDateTime timestamp,
    @Schema(description = "ID de correlação da requisição (mesmo valor do header X-Correlation-ID)",
        example = "9f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f", nullable = true)
    String correlationId,
    @Schema(description = "Detalhes por campo, presente em erros de validação (422)", nullable = true)
    List<FieldError> fieldErrors
) {
    public ErrorResponse(int status, String error, String message) {
        this(status, error, message, null, OffsetDateTime.now(ZoneOffset.UTC), correlationIdAtual(), null);
    }

    public ErrorResponse(int status, String error, String message, String code) {
        this(status, error, message, code, OffsetDateTime.now(ZoneOffset.UTC), correlationIdAtual(), null);
    }

    public ErrorResponse(int status, String error, String message, List<FieldError> fieldErrors) {
        this(status, error, message, null, OffsetDateTime.now(ZoneOffset.UTC), correlationIdAtual(), fieldErrors);
    }

    private static String correlationIdAtual() {
        return MDC.get(LoggingKeys.CORRELATION_ID);
    }

    @Schema(name = "FieldError", description = "Erro de validação em um campo específico.")
    public record FieldError(
        @Schema(description = "Nome do campo inválido", example = "email") String field,
        @Schema(description = "Motivo da invalidação", example = "deve ser um endereço de e-mail válido") String message) {
    }
}
