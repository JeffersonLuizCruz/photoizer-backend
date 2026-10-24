package com.photoizer.crm.shared.exception;

import com.photoizer.crm.shared.logging.SensitiveDataMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * PATTERN: Exception Hierarchy Handler
 *
 * Tratamento global centralizado da hierarquia {@link BusinessException} e das
 * exceções do Spring MVC.
 *
 * <p>Política de logs:
 * <ul>
 *   <li>4xx de negócio (404/409/422...): WARN, sem stack trace (regra esperada);</li>
 *   <li>5xx de negócio ou exceção inesperada: ERROR com stack trace, uma única vez;</li>
 *   <li>o correlationId já vai no MDC e é anexado ao {@link ErrorResponse};</li>
 *   <li>mensagens são mascaradas antes de ir para o log (PII).</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ═══════════════════════════════════════════════════════════
    // Hierarquia BusinessException (um único ponto de decisão de nível)
    // ═══════════════════════════════════════════════════════════

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        var status = e.getHttpStatus() != null ? e.getHttpStatus() : HttpStatus.INTERNAL_SERVER_ERROR;

        if (status.is5xxServerError()) {
            // Falha de negócio inesperada: o stack trace é útil.
            log.error("Falha de negócio {}", status.value(), e);
        } else {
            // Regra esperada (404/409/422...): WARN sem stack trace.
            log.warn("[{}] {} (code={})",
                status.value(), SensitiveDataMask.mask(e.getMessage()), e.getErrorCode());
        }
        return build(status, e);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurity(SecurityException e) {
        log.warn("[403] Tentativa de acesso não autorizado: {}", SensitiveDataMask.mask(e.getMessage()));
        return build(HttpStatus.FORBIDDEN, "Acesso negado");
    }

    // ═══════════════════════════════════════════════════════════
    // Exceções do Spring MVC
    // ═══════════════════════════════════════════════════════════

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        var fieldErrors = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();
        log.warn("Erro de validação em {} campo(s)", fieldErrors.size());
        return ResponseEntity.unprocessableEntity().body(new ErrorResponse(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
            "Um ou mais campos estão inválidos",
            fieldErrors));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
                       MethodArgumentTypeMismatchException.class,
                       MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleRequisicaoInvalida(Exception e) {
        log.warn("Requisição inválida: {} - {}",
            e.getClass().getSimpleName(), SensitiveDataMask.mask(e.getMessage()));
        return build(HttpStatus.BAD_REQUEST, "Requisição inválida");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        log.warn("[404] Recurso não encontrado: {}", SensitiveDataMask.mask(e.getMessage()));
        return build(HttpStatus.NOT_FOUND, "Recurso não encontrado");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("Upload excedeu o tamanho máximo");
        return build(HttpStatus.PAYLOAD_TOO_LARGE,
            "O envio excedeu o limite de tamanho permitido. Envie menos fotos por vez.");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException e) {
        log.warn("Credenciais inválidas");
        return build(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        log.warn("Campo obrigatório ausente: {}", SensitiveDataMask.mask(e.getRequestPartName()));
        return build(HttpStatus.UNPROCESSABLE_ENTITY,
            "Campo obrigatório não enviado: " + e.getRequestPartName());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        log.warn("[403] Acesso negado: {}", SensitiveDataMask.mask(e.getMessage()));
        return build(HttpStatus.FORBIDDEN, "Acesso negado");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        // Não logar e.getMessage(): o SQL pode conter valores de PII.
        log.warn("Violação de integridade: {}", e.getMostSpecificCause().getClass().getSimpleName());
        return build(HttpStatus.CONFLICT,
            "Violação de integridade de dados: registro duplicado ou referência inválida");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        // ÚNICO lugar que loga stack trace de erro inesperado.
        log.error("Erro interno não tratado", e);
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
        var code = e.getErrorCode() != null ? e.getErrorCode().name() : null;
        return ResponseEntity.status(status)
            .body(new ErrorResponse(status.value(), status.getReasonPhrase(), e.getMessage(), code));
    }
}
