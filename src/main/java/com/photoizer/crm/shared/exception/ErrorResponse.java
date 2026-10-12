package com.photoizer.crm.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

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
    @Schema(description = "Detalhes por campo, presente em erros de validação (422)", nullable = true)
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

    @Schema(name = "FieldError", description = "Erro de validação em um campo específico.")
    public record FieldError(
        @Schema(description = "Nome do campo inválido", example = "email") String field,
        @Schema(description = "Motivo da invalidação", example = "deve ser um endereço de e-mail válido") String message) {}
}
