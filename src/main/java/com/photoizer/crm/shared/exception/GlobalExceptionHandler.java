package com.photoizer.crm.shared.exception;

import com.photoizer.crm.agenda.exception.AgendamentoNaoEncontradoException;
import com.photoizer.crm.agenda.exception.AgendamentoNoPassadoException;
import com.photoizer.crm.agenda.exception.ConflitoDeAgendaException;
import com.photoizer.crm.agenda.exception.EditorNaoEncontradoException;
import com.photoizer.crm.agenda.exception.PacoteInativoException;
import com.photoizer.crm.agenda.exception.PacoteNaoEncontradoException;
import com.photoizer.crm.agenda.exception.TarefaNaoEncontradaException;
import com.photoizer.crm.agenda.exception.TarefaNaoPodeSerExcluidaException;
import com.photoizer.crm.cliente.exception.ClienteNaoEncontradoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ClienteNaoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleClienteNaoEncontrado(ClienteNaoEncontradoException e) {
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(PacoteNaoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handlePacoteNaoEncontrado(PacoteNaoEncontradoException e) {
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(EditorNaoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleEditorNaoEncontrado(EditorNaoEncontradoException e) {
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(AgendamentoNaoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleAgendamentoNaoEncontrado(AgendamentoNaoEncontradoException e) {
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(PacoteInativoException.class)
    public ResponseEntity<ErrorResponse> handlePacoteInativo(PacoteInativoException e) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(AgendamentoNoPassadoException.class)
    public ResponseEntity<ErrorResponse> handleAgendamentoNoPassado(AgendamentoNoPassadoException e) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(TarefaNaoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleTarefaNaoEncontrada(TarefaNaoEncontradaException e) {
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(TarefaNaoPodeSerExcluidaException.class)
    public ResponseEntity<ErrorResponse> handleTarefaNaoPodeSerExcluida(TarefaNaoPodeSerExcluidaException e) {
        return build(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(ConflitoDeAgendaException.class)
    public ResponseEntity<ErrorResponse> handleConflitoDeAgenda(ConflitoDeAgendaException e) {
        return build(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
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
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo excede o tamanho máximo permitido de 10MB");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "Campo obrigatório não enviado: " + e.getRequestPartName());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
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
