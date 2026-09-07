package com.photoizer.crm.shared.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * PATTERN: Exception Hierarchy Handler
 *
 * GlobalExceptionHandler com hierarquia de exceções centralizada.
 *
 * ANTES: 50+ handlers, cada um mapeando uma exceção de domínio específica.
 * Importava ~60 classes de exceção de 14 módulos de negócio.
 * Violação Modulith: infraestrutura dependia do domínio.
 *
 * DEPOIS: 5 handlers genéricos base + handlers para exceções Spring.
 * Zero imports de módulos de negócio — depende apenas de shared.exception.*.
 *
 * A hierarquia BusinessException → {NotFound, Conflict, Unprocessable,
 * Gone, Unauthorized, Forbidden, BadRequest}Exception permite mapear
 * HTTP status automaticamente a partir do tipo da exceção.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ═══════════════════════════════════════════════════════════
    // Handlers da hierarquia BusinessException (5 handlers → 50+ exceções)
    // ═══════════════════════════════════════════════════════════

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        log.warn("[404] {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException e) {
        log.warn("[400] {}", e.getMessage());
        return build(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException e) {
        log.warn("[409] {}", e.getMessage());
        return build(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(UnprocessableException.class)
    public ResponseEntity<ErrorResponse> handleUnprocessable(UnprocessableException e) {
        log.warn("[422] {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(GoneException.class)
    public ResponseEntity<ErrorResponse> handleGone(GoneException e) {
        log.warn("[410] {}", e.getMessage());
        return build(HttpStatus.GONE, e);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException e) {
        log.warn("[401] {}", e.getMessage());
        return build(HttpStatus.UNAUTHORIZED, e);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
        log.warn("[403] {}", e.getMessage());
        return build(HttpStatus.FORBIDDEN, e);
    }

    /**
     * Fallback para qualquer BusinessException que não tenha um handler específico.
     * Captura EdicaoBusinessException, NotificacaoBusinessException e qualquer
     * futura subclasse que não extenda uma marcadora (NotFound, Conflict, etc.).
     * Usa e.getHttpStatus() para derivar o status HTTP correto.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        var status = e.getHttpStatus() != null ? e.getHttpStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
        log.warn("[{}] {}", status.value(), e.getMessage());
        return build(status, e);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurity(SecurityException e) {
        log.warn("[403] Tentativa de acesso não autorizado: {}", e.getMessage());
        return build(HttpStatus.FORBIDDEN, "Acesso negado");
    }

    // ═══════════════════════════════════════════════════════════
    // Handlers para exceções Spring (mantidos — não são BusinessException)
    // ═══════════════════════════════════════════════════════════

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        log.warn("Erro de validacao: {}", e.getMessage());
        var fieldErrors = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();
        return ResponseEntity.unprocessableEntity().body(new ErrorResponse(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
            "Um ou mais campos estão inválidos",
            fieldErrors
        ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("Upload excedeu tamanho maximo: {}", e.getMessage());
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo excede o tamanho máximo permitido de 10MB");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException e) {
        log.warn("Credenciais invalidas: {}", e.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        log.warn("Campo obrigatorio ausente: {}", e.getRequestPartName());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "Campo obrigatório não enviado: " + e.getRequestPartName());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        log.warn("Acesso negado: {}", e.getMessage());
        return build(HttpStatus.FORBIDDEN, "Acesso negado");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("Violação de integridade de dados: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, "Violação de integridade de dados: registro duplicado ou referência inválida");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        log.error("Erro interno nao tratado", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor");
    }

    // ═══════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
            .body(new ErrorResponse(status.value(), status.getReasonPhrase(), message));
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, BusinessException e) {
        String code = e.getErrorCode() != null ? e.getErrorCode().name() : null;
        return ResponseEntity.status(status)
            .body(new ErrorResponse(status.value(), status.getReasonPhrase(), e.getMessage(), code));
    }
}
