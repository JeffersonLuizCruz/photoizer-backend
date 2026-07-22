package com.photoizer.crm.shared.exception;

import com.photoizer.crm.agenda.exception.AgendamentoNaoEncontradoException;
import com.photoizer.crm.agenda.exception.AgendamentoNoPassadoException;
import com.photoizer.crm.agenda.exception.ConflitoDeAgendaException;
import com.photoizer.crm.agenda.exception.EditorNaoEncontradoException;
import com.photoizer.crm.pacote.exception.PacoteInativoException;
import com.photoizer.crm.pacote.exception.PacoteNaoEncontradoException;
import com.photoizer.crm.agenda.exception.TarefaNaoEncontradaException;
import com.photoizer.crm.agenda.exception.TarefaNaoPodeSerExcluidaException;
import com.photoizer.crm.cliente.exception.ClienteNaoEncontradoException;
import com.photoizer.crm.comissao.exception.IndicacaoNaoEncontradaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @ExceptionHandler(TarefaNaoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleTarefaNaoEncontrada(TarefaNaoEncontradaException e) {
        log.warn("Tarefa nao encontrada: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(TarefaNaoPodeSerExcluidaException.class)
    public ResponseEntity<ErrorResponse> handleTarefaNaoPodeSerExcluida(TarefaNaoPodeSerExcluidaException e) {
        log.warn("Tarefa nao pode ser excluida: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(IndicacaoNaoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleIndicacaoNaoEncontrada(IndicacaoNaoEncontradaException e) {
        log.warn("Indicacao nao encontrada: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(ConflitoDeAgendaException.class)
    public ResponseEntity<ErrorResponse> handleConflitoDeAgenda(ConflitoDeAgendaException e) {
        log.warn("Conflito de agenda: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, e);
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
