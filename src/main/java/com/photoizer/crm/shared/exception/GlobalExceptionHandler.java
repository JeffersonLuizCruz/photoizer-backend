package com.photoizer.crm.shared.exception;

import com.photoizer.crm.despesa.exception.CategoriaDespesaNaoEncontradaException;
import com.photoizer.crm.despesa.exception.CategoriaDuplicadaException;
import com.photoizer.crm.despesa.exception.CategoriaEmUsoException;
import com.photoizer.crm.despesa.exception.DespesaNaoEncontradaException;
import com.photoizer.crm.despesa.exception.DespesaRecorrenteNaoPagaException;
import org.springframework.security.access.AccessDeniedException;
import com.photoizer.crm.agenda.exception.AgendamentoNaoEncontradoException;
import com.photoizer.crm.agenda.exception.AgendamentoNoPassadoException;
import com.photoizer.crm.agenda.exception.ConflitoDeAgendaException;
import com.photoizer.crm.agenda.exception.EditorNaoEncontradoException;
import com.photoizer.crm.agenda.exception.EnsaioNaoFinalizadoException;
import com.photoizer.crm.pacote.exception.PacoteInativoException;
import com.photoizer.crm.pacote.exception.PacoteNaoEncontradoException;

import com.photoizer.crm.cliente.exception.ClienteNaoEncontradoException;
import com.photoizer.crm.edicao.exception.EdicaoNaoEncontradaException;
import com.photoizer.crm.edicao.exception.FotoEdicaoNaoEncontradaException;
import com.photoizer.crm.edicao.exception.FotoSemRawException;
import com.photoizer.crm.edicao.exception.StatusEdicaoInvalidoException;
import com.photoizer.crm.ecommerce.exception.TokenExpiradoException;
import com.photoizer.crm.contrato.exception.ContratoEstadoInvalidoException;
import com.photoizer.crm.contrato.exception.ContratoNaoEncontradoException;
import com.photoizer.crm.contrato.exception.ContratoTokenExpiradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ClienteNaoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleClienteNaoEncontrado(ClienteNaoEncontradoException e) {
        log.warn("Cliente nao encontrado: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(PacoteNaoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handlePacoteNaoEncontrado(PacoteNaoEncontradoException e) {
        log.warn("Pacote nao encontrado: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(EditorNaoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleEditorNaoEncontrado(EditorNaoEncontradoException e) {
        log.warn("Editor nao encontrado: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(AgendamentoNaoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleAgendamentoNaoEncontrado(AgendamentoNaoEncontradoException e) {
        log.warn("Agendamento nao encontrado: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(PacoteInativoException.class)
    public ResponseEntity<ErrorResponse> handlePacoteInativo(PacoteInativoException e) {
        log.warn("Pacote inativo: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(AgendamentoNoPassadoException.class)
    public ResponseEntity<ErrorResponse> handleAgendamentoNoPassado(AgendamentoNoPassadoException e) {
        log.warn("Agendamento no passado: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(EdicaoNaoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleEdicaoNaoEncontrada(EdicaoNaoEncontradaException e) {
        log.warn("Edicao nao encontrada: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(FotoEdicaoNaoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleFotoEdicaoNaoEncontrada(FotoEdicaoNaoEncontradaException e) {
        log.warn("Foto edicao nao encontrada: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(FotoSemRawException.class)
    public ResponseEntity<ErrorResponse> handleFotoSemRaw(FotoSemRawException e) {
        log.warn("Foto sem RAW correspondente: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(StatusEdicaoInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleStatusEdicaoInvalido(StatusEdicaoInvalidoException e) {
        log.warn("Status de edicao invalido: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(TokenExpiradoException.class)
    public ResponseEntity<ErrorResponse> handleTokenExpirado(TokenExpiradoException e) {
        log.warn("Token da galeria expirado: {}", e.getMessage());
        return build(HttpStatus.GONE, e);
    }

    @ExceptionHandler(ContratoNaoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleContratoNaoEncontrado(ContratoNaoEncontradoException e) {
        log.warn("Contrato nao encontrado: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(ContratoEstadoInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleContratoEstadoInvalido(ContratoEstadoInvalidoException e) {
        log.warn("Estado invalido do contrato: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(ContratoTokenExpiradoException.class)
    public ResponseEntity<ErrorResponse> handleContratoTokenExpirado(ContratoTokenExpiradoException e) {
        log.warn("Token do contrato expirado: {}", e.getMessage());
        return build(HttpStatus.GONE, e);
    }

    @ExceptionHandler(com.photoizer.crm.config.exception.ConfiguracaoInvalidaException.class)
    public ResponseEntity<ErrorResponse> handleConfiguracaoInvalida(com.photoizer.crm.config.exception.ConfiguracaoInvalidaException e) {
        log.warn("Configuracao invalida: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException e) {
        log.warn("Credenciais invalidas: {}", e.getMessage());
        return build(HttpStatus.UNAUTHORIZED, e);
    }

    @ExceptionHandler(ConflitoDeAgendaException.class)
    public ResponseEntity<ErrorResponse> handleConflitoDeAgenda(ConflitoDeAgendaException e) {
        log.warn("Conflito de agenda: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(EnsaioNaoFinalizadoException.class)
    public ResponseEntity<ErrorResponse> handleEnsaioNaoFinalizado(EnsaioNaoFinalizadoException e) {
        log.warn("Ensaio nao finalizado: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        log.warn("Erro de validacao: {}", e.getMessage());
        var fieldErrors = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();
        var body = new ErrorResponse(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            "Validation Error",
            "Um ou mais campos estão inválidos",
            java.time.LocalDateTime.now(),
            fieldErrors
        );
        return ResponseEntity.unprocessableEntity().body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("Upload excedeu tamanho maximo: {}", e.getMessage());
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo excede o tamanho máximo permitido de 10MB");
    }

    @ExceptionHandler(DespesaNaoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleDespesaNaoEncontrada(DespesaNaoEncontradaException e) {
        log.warn("Despesa nao encontrada: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(CategoriaDespesaNaoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleCategoriaDespesaNaoEncontrada(CategoriaDespesaNaoEncontradaException e) {
        log.warn("Categoria de despesa nao encontrada: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(CategoriaEmUsoException.class)
    public ResponseEntity<ErrorResponse> handleCategoriaEmUso(CategoriaEmUsoException e) {
        log.warn("Categoria em uso: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(CategoriaDuplicadaException.class)
    public ResponseEntity<ErrorResponse> handleCategoriaDuplicada(CategoriaDuplicadaException e) {
        log.warn("Categoria duplicada: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(DespesaRecorrenteNaoPagaException.class)
    public ResponseEntity<ErrorResponse> handleDespesaRecorrenteNaoPaga(DespesaRecorrenteNaoPagaException e) {
        log.warn("Tentativa de pagar despesa recorrente: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Argumento invalido: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        log.error("Erro interno nao tratado", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
            .body(new ErrorResponse(status.value(), status.getReasonPhrase(), message));
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, RuntimeException e) {
        return ResponseEntity.status(status)
            .body(new ErrorResponse(status.value(), status.getReasonPhrase(), e.getMessage()));
    }
}
