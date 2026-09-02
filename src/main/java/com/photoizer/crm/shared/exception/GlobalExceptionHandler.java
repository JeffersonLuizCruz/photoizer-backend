package com.photoizer.crm.shared.exception;

import com.photoizer.crm.despesa.exception.CategoriaDespesaNaoEncontradaException;
import com.photoizer.crm.despesa.exception.CategoriaDuplicadaException;
import com.photoizer.crm.despesa.exception.CategoriaEmUsoException;
import com.photoizer.crm.despesa.exception.CategoriaObrigatoriaException;
import com.photoizer.crm.despesa.exception.DespesaNaoEncontradaException;
import com.photoizer.crm.despesa.exception.DespesaRecorrenteNaoPagaException;
import com.photoizer.crm.despesa.exception.StatusDespesaInvalidoException;
import com.photoizer.crm.despesa.exception.AgendamentoVinculadoInvalidoException;
import org.springframework.security.access.AccessDeniedException;
import com.photoizer.crm.agenda.exception.AgendamentoNaoEncontradoException;
import com.photoizer.crm.agenda.exception.AgendamentoNoPassadoException;
import com.photoizer.crm.agenda.exception.ComprovanteObrigatorioException;
import com.photoizer.crm.agenda.exception.ConflitoDeAgendaException;
import com.photoizer.crm.agenda.exception.EditorNaoEncontradoException;
import com.photoizer.crm.agenda.exception.EnsaioNaoFinalizadoException;
import com.photoizer.crm.agenda.exception.FotografoNaoEncontradoException;
import com.photoizer.crm.agenda.exception.PagamentoInsuficienteException;
import com.photoizer.crm.agenda.exception.StatusAgendamentoInvalidoException;
import com.photoizer.crm.financeiro.exception.AgendamentoNaoEncontradoParaFinanceiroException;
import com.photoizer.crm.financeiro.exception.ClienteObrigatorioException;
import com.photoizer.crm.financeiro.exception.IndicadorInvalidoException;
import com.photoizer.crm.financeiro.exception.OperacaoNaoPermitidaException;
import com.photoizer.crm.financeiro.exception.PacoteNaoEncontradoParaPreviewException;
import com.photoizer.crm.financeiro.exception.PagamentoNaoEncontradoException;
import com.photoizer.crm.financeiro.exception.ReceitaNaoEncontradaException;
import com.photoizer.crm.financeiro.exception.ValorInvalidoException;
import com.photoizer.crm.financeiro.exception.ValorRecebidoExcedeFinalException;
import com.photoizer.crm.pacote.exception.PacoteInativoException;
import com.photoizer.crm.pacote.exception.PacoteNaoEncontradoException;

import com.photoizer.crm.cliente.exception.ClienteNaoEncontradoException;
import com.photoizer.crm.edicao.exception.EdicaoNaoEncontradaException;
import com.photoizer.crm.edicao.exception.FotoEdicaoNaoEncontradaException;
import com.photoizer.crm.edicao.exception.FotoSemRawException;
import com.photoizer.crm.edicao.exception.StatusEdicaoInvalidoException;
import com.photoizer.crm.edicao.exception.EdicaoBusinessException;
import com.photoizer.crm.ecommerce.exception.CarrinhoVazioException;
import com.photoizer.crm.ecommerce.exception.CompraJaPagaException;
import com.photoizer.crm.ecommerce.exception.CompraNaoEncontradaException;
import com.photoizer.crm.ecommerce.exception.FotoIndisponivelException;
import com.photoizer.crm.ecommerce.exception.FotoJaBaixadaException;
import com.photoizer.crm.ecommerce.exception.FotoJaSelecionadaException;
import com.photoizer.crm.ecommerce.exception.FotoNaoEncontradaException;
import com.photoizer.crm.ecommerce.exception.GaleriaNaoEncontradaException;
import com.photoizer.crm.ecommerce.exception.LimitePacoteExcedidoException;
import com.photoizer.crm.ecommerce.exception.SessaoInvalidaException;
import com.photoizer.crm.ecommerce.exception.TokenExpiradoException;
import com.photoizer.crm.contrato.exception.ContratoEstadoInvalidoException;
import com.photoizer.crm.contrato.exception.ContratoNaoEncontradoException;
import com.photoizer.crm.contrato.exception.ContratoTokenExpiradoException;
import com.photoizer.crm.documento.exception.TipoComprovanteInvalidoException;
import com.photoizer.crm.foto.exception.AgendamentoNaoPermitidoParaUploadException;
import com.photoizer.crm.foto.exception.FotoEnsaioNaoEncontradaException;
import com.photoizer.crm.foto.exception.FotoNaoPertenceAoAgendamentoException;
import com.photoizer.crm.foto.exception.StatusFotoInvalidoException;
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

    @ExceptionHandler(FotografoNaoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleFotografoNaoEncontrado(FotografoNaoEncontradoException e) {
        log.warn("Fotografo nao encontrado: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(com.photoizer.crm.fotografo.exception.FotografoComEnsaiosVinculadosException.class)
    public ResponseEntity<ErrorResponse> handleFotografoComEnsaiosVinculados(com.photoizer.crm.fotografo.exception.FotografoComEnsaiosVinculadosException e) {
        log.warn("Fotografo com ensaios vinculados: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
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

    @ExceptionHandler(EdicaoBusinessException.class)
    public ResponseEntity<ErrorResponse> handleEdicaoBusiness(EdicaoBusinessException e) {
        log.warn("Exceção de negócio do módulo edição: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(TokenExpiradoException.class)
    public ResponseEntity<ErrorResponse> handleTokenExpirado(TokenExpiradoException e) {
        log.warn("Token da galeria expirado: {}", e.getMessage());
        return build(HttpStatus.GONE, e);
    }

    @ExceptionHandler(GaleriaNaoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleGaleriaNaoEncontrada(GaleriaNaoEncontradaException e) {
        log.warn("Galeria nao encontrada: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(CompraNaoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleCompraNaoEncontrada(CompraNaoEncontradaException e) {
        log.warn("Compra nao encontrada: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(FotoNaoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleFotoNaoEncontrada(FotoNaoEncontradaException e) {
        log.warn("Foto nao encontrada: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(CarrinhoVazioException.class)
    public ResponseEntity<ErrorResponse> handleCarrinhoVazio(CarrinhoVazioException e) {
        log.warn("Carrinho vazio: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(FotoJaSelecionadaException.class)
    public ResponseEntity<ErrorResponse> handleFotoJaSelecionada(FotoJaSelecionadaException e) {
        log.warn("Foto ja selecionada: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(FotoJaBaixadaException.class)
    public ResponseEntity<ErrorResponse> handleFotoJaBaixada(FotoJaBaixadaException e) {
        log.warn("Foto ja baixada: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(LimitePacoteExcedidoException.class)
    public ResponseEntity<ErrorResponse> handleLimitePacoteExcedido(LimitePacoteExcedidoException e) {
        log.warn("Limite do pacote excedido: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(CompraJaPagaException.class)
    public ResponseEntity<ErrorResponse> handleCompraJaPaga(CompraJaPagaException e) {
        log.warn("Compra ja paga: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(SessaoInvalidaException.class)
    public ResponseEntity<ErrorResponse> handleSessaoInvalida(SessaoInvalidaException e) {
        log.warn("Sessao invalida: {}", e.getMessage());
        return build(HttpStatus.UNAUTHORIZED, e);
    }

    @ExceptionHandler(FotoIndisponivelException.class)
    public ResponseEntity<ErrorResponse> handleFotoIndisponivel(FotoIndisponivelException e) {
        log.warn("Foto indisponivel: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
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

    @ExceptionHandler(ComprovanteObrigatorioException.class)
    public ResponseEntity<ErrorResponse> handleComprovanteObrigatorio(ComprovanteObrigatorioException e) {
        log.warn("Comprovante obrigatorio: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(PagamentoInsuficienteException.class)
    public ResponseEntity<ErrorResponse> handlePagamentoInsuficiente(PagamentoInsuficienteException e) {
        log.warn("Pagamento insuficiente: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(StatusAgendamentoInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleStatusAgendamentoInvalido(StatusAgendamentoInvalidoException e) {
        log.warn("Transição de status de agendamento inválida: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(AgendamentoNaoEncontradoParaFinanceiroException.class)
    public ResponseEntity<ErrorResponse> handleAgendamentoNaoEncontradoFinanceiro(AgendamentoNaoEncontradoParaFinanceiroException e) {
        log.warn("Agendamento nao encontrado para financeiro: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(PagamentoNaoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handlePagamentoNaoEncontrado(PagamentoNaoEncontradoException e) {
        log.warn("Pagamento nao encontrado: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(ReceitaNaoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleReceitaNaoEncontrada(ReceitaNaoEncontradaException e) {
        log.warn("Receita nao encontrada: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(PacoteNaoEncontradoParaPreviewException.class)
    public ResponseEntity<ErrorResponse> handlePacoteNaoEncontradoPreview(PacoteNaoEncontradoParaPreviewException e) {
        log.warn("Pacote nao encontrado para preview: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(OperacaoNaoPermitidaException.class)
    public ResponseEntity<ErrorResponse> handleOperacaoNaoPermitida(OperacaoNaoPermitidaException e) {
        log.warn("Operacao nao permitida: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(ValorInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleValorInvalido(ValorInvalidoException e) {
        log.warn("Valor invalido: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(IndicadorInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleIndicadorInvalido(IndicadorInvalidoException e) {
        log.warn("Indicador invalido: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(ClienteObrigatorioException.class)
    public ResponseEntity<ErrorResponse> handleClienteObrigatorio(ClienteObrigatorioException e) {
        log.warn("Cliente obrigatorio: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(ValorRecebidoExcedeFinalException.class)
    public ResponseEntity<ErrorResponse> handleValorRecebidoExcedeFinal(ValorRecebidoExcedeFinalException e) {
        log.warn("Valor recebido excede final: {}", e.getMessage());
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

    @ExceptionHandler(StatusDespesaInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleStatusDespesaInvalido(StatusDespesaInvalidoException e) {
        log.warn("Transição de status de despesa inválida: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(AgendamentoVinculadoInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleAgendamentoVinculadoInvalido(AgendamentoVinculadoInvalidoException e) {
        log.warn("Agendamento vinculado inválido: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(CategoriaObrigatoriaException.class)
    public ResponseEntity<ErrorResponse> handleCategoriaObrigatoria(CategoriaObrigatoriaException e) {
        log.warn("Categoria obrigatória não informada: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(TipoComprovanteInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleTipoComprovanteInvalido(TipoComprovanteInvalidoException e) {
        log.warn("Tipo de comprovante invalido: {}", e.getMessage());
        return build(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler(FotoEnsaioNaoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleFotoEnsaioNaoEncontrada(FotoEnsaioNaoEncontradaException e) {
        log.warn("Foto não encontrada: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(FotoNaoPertenceAoAgendamentoException.class)
    public ResponseEntity<ErrorResponse> handleFotoNaoPertenceAoAgendamento(FotoNaoPertenceAoAgendamentoException e) {
        log.warn("Foto nao pertence ao agendamento: {}", e.getMessage());
        return build(HttpStatus.FORBIDDEN, e);
    }

    @ExceptionHandler(AgendamentoNaoPermitidoParaUploadException.class)
    public ResponseEntity<ErrorResponse> handleAgendamentoNaoPermitidoParaUpload(AgendamentoNaoPermitidoParaUploadException e) {
        log.warn("Upload nao permitido: {}", e.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(StatusFotoInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleStatusFotoInvalido(StatusFotoInvalidoException e) {
        log.warn("Transição de status de foto inválida: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, e);
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
