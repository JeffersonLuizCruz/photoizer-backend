package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.service.AgendamentoService;
import com.photoizer.crm.agenda.service.CriarAgendamentoCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agendamentos")
@Tag(name = "Agendamentos", description = "Gestão de agendamentos de ensaios fotográficos")
public class AgendamentoController {

    private final AgendamentoService agendamentoService;

    public AgendamentoController(AgendamentoService agendamentoService) {
        this.agendamentoService = agendamentoService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Criar novo agendamento", description = "Cria um agendamento com upload do comprovante de pagamento")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Agendamento criado com sucesso"),
        @ApiResponse(responseCode = "422", description = "Dados inválidos", content = @Content),
        @ApiResponse(responseCode = "409", description = "Conflito de agenda", content = @Content),
        @ApiResponse(responseCode = "413", description = "Arquivo excede o tamanho máximo", content = @Content)
    })
    public ResponseEntity<AgendamentoResponse> criar(
            @RequestParam(required = false) @Parameter(description = "ID do cliente (se existente)") UUID clienteId,
            @RequestParam(required = false) @Parameter(description = "Nome do cliente (para novo cliente)") String nome,
            @RequestParam(required = false) @Parameter(description = "Telefone do cliente (para novo cliente)") String telefone,
            @RequestParam(required = false) @Parameter(description = "Email do cliente") String email,
            @RequestParam(required = false) @Parameter(description = "CPF do cliente") String cpf,
            @RequestParam(required = false) @Parameter(description = "Cidade do cliente") String cidade,
            @RequestParam(required = false) @Parameter(description = "Estado do cliente") String estado,
            @RequestParam(required = false) @Parameter(description = "Origem do cliente (INDICACAO, ANUNCIO, OUTROS)") String origem,
            @RequestParam @Parameter(description = "ID do pacote") UUID pacoteId,
            @RequestParam(required = false) @Parameter(description = "ID do editor (opcional)") UUID editorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(description = "Data e hora do ensaio (ISO 8601)") LocalDateTime dataHoraEnsaio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Parameter(description = "Data do ensaio (alternativa ao dataHoraEnsaio)") LocalDate data,
            @RequestParam(required = false) @Parameter(description = "Hora do ensaio (alternativa ao dataHoraEnsaio, formato HH:mm)") String hora,
            @RequestParam(required = false, defaultValue = "60")
            @Parameter(description = "Duração em minutos (default: 60)") Integer duracaoMinutos,
            @RequestParam @Parameter(description = "Local do ensaio") String localEnsaio,
            @RequestParam(required = false) @Parameter(description = "Endereço completo (opcional)") String enderecoCompleto,
            @RequestParam(required = false) @Parameter(description = "Taxa de deslocamento (opcional)") BigDecimal taxaDeslocamento,
            @RequestParam(required = false) @Parameter(description = "Comprovante de pagamento (PDF, JPG, PNG - max 10MB)") MultipartFile comprovanteEntrada,
            @RequestParam(required = false) @Parameter(description = "Autoriza uso de imagem (default: false)") Boolean autorizaUsoImagem,
            @RequestParam(required = false) @Parameter(description = "Cláusulas personalizadas (opcional)") String clausulasPersonalizadas,
            @RequestParam(required = false) @Parameter(description = "Observações (opcional)") String observacoes
    ) {
        validarComprovante(comprovanteEntrada);

        var command = new CriarAgendamentoCommand(
            clienteId, nome, telefone, email, cpf, cidade, estado, origem,
            pacoteId, editorId, dataHoraEnsaio, data, hora, duracaoMinutos,
            localEnsaio, enderecoCompleto, taxaDeslocamento,
            comprovanteEntrada, autorizaUsoImagem, clausulasPersonalizadas, observacoes
        );

        var agendamento = agendamentoService.criarAgendamento(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(AgendamentoResponse.of(agendamento));
    }

    @GetMapping
    @Operation(summary = "Listar agendamentos", description = "Retorna agendamentos com suporte a filtros")
    @ApiResponse(responseCode = "200", description = "Lista de agendamentos")
    public ResponseEntity<List<AgendamentoResponse>> listar(
            @RequestParam(required = false) @Parameter(description = "Filtrar por status") String status,
            @RequestParam(required = false) @Parameter(description = "Filtrar por editor") UUID editorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(description = "Data início") LocalDateTime dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(description = "Data fim") LocalDateTime dataFim,
            @RequestParam(required = false) @Parameter(description = "Buscar por nome do cliente") String search) {
        StatusAgendamento statusEnum = null;
        if (status != null && !status.isBlank()) {
            statusEnum = StatusAgendamento.valueOf(status);
        }
        var agendamentos = agendamentoService.listarTodos(editorId, statusEnum, dataInicio, dataFim, search).stream()
            .map(AgendamentoResponse::of)
            .toList();
        return ResponseEntity.ok(agendamentos);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar agendamento")
    public ResponseEntity<AgendamentoResponse> atualizar(
            @PathVariable UUID id,
            @RequestBody @Valid AtualizarAgendamentoRequest request) {
        return ResponseEntity.ok(agendamentoService.atualizar(id, request));
    }

    @GetMapping("/verificar-disponibilidade")
    @Operation(summary = "Verificar disponibilidade de horário")
    public ResponseEntity<DisponibilidadeResponse> verificarDisponibilidade(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate data,
            @RequestParam String hora,
            @RequestParam(defaultValue = "60") Integer duracaoMinutos,
            @RequestParam(required = false) UUID excluirAgendamentoId) {
        return ResponseEntity.ok(agendamentoService.verificarDisponibilidade(data, hora, duracaoMinutos, excluirAgendamentoId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar agendamento por ID", description = "Retorna os detalhes de um agendamento específico")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Agendamento encontrado"),
        @ApiResponse(responseCode = "404", description = "Agendamento não encontrado", content = @Content)
    })
    public ResponseEntity<AgendamentoResponse> buscarPorId(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id) {
        var agendamento = agendamentoService.buscarPorId(id);
        return ResponseEntity.ok(AgendamentoResponse.of(agendamento));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Atualizar status do agendamento")
    public ResponseEntity<AgendamentoResponse> atualizarStatus(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id,
            @RequestBody Map<String, String> body) {
        var status = body.get("status");
        var agendamento = agendamentoService.atualizarStatus(id, status);
        return ResponseEntity.ok(AgendamentoResponse.of(agendamento));
    }

    @PatchMapping("/{id}/reagendar")
    @Operation(summary = "Reagendar ensaio", description = "Atualiza data/hora e redefine status para CONFIRMADO")
    public ResponseEntity<AgendamentoResponse> reagendar(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Parameter(description = "Nova data") LocalDate data,
            @RequestParam(required = false) @Parameter(description = "Novo horário (HH:mm)") String hora,
            @RequestParam(required = false) @Parameter(description = "Nova duração em minutos") Integer duracaoMinutos) {
        var agendamento = agendamentoService.reagendar(id, data, hora, duracaoMinutos);
        return ResponseEntity.ok(AgendamentoResponse.of(agendamento));
    }

    @PatchMapping("/{id}/destaque")
    @Operation(summary = "Alternar destaque do ensaio")
    public ResponseEntity<AgendamentoResponse> toggleDestaque(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id) {
        var agendamento = agendamentoService.toggleDestaque(id);
        return ResponseEntity.ok(AgendamentoResponse.of(agendamento));
    }

    @PostMapping("/{id}/pagamento-final")
    @Operation(summary = "Registrar pagamento final", description = "Registra o pagamento final com comprovante opcional")
    public ResponseEntity<AgendamentoResponse> registrarPagamentoFinal(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id,
            @RequestParam(required = false) @Parameter(description = "Comprovante de pagamento final") MultipartFile comprovanteFinal) {
        var agendamento = agendamentoService.registrarPagamentoFinal(id, comprovanteFinal);
        return ResponseEntity.ok(AgendamentoResponse.of(agendamento));
    }

    private void validarComprovante(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Comprovante de pagamento é obrigatório");
        }
        var contentType = arquivo.getContentType();
        if (contentType == null || !List.of("application/pdf", "image/jpeg", "image/png").contains(contentType)) {
            throw new IllegalArgumentException("Tipo de arquivo inválido. Permitidos: PDF, JPG, PNG");
        }
    }
}
