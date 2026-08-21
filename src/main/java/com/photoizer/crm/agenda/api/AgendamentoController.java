package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.agenda.service.AgendamentoService;
import com.photoizer.crm.agenda.service.AgendamentoStatusLifecycle;
import com.photoizer.crm.agenda.service.CriarAgendamentoCommand;
import com.photoizer.crm.agenda.service.DisponibilidadeService;
import com.photoizer.crm.comissao.repository.IndicacaoRepository;
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

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agendamentos")
@Tag(name = "Agendamentos", description = "Gestão de agendamentos de ensaios fotográficos")
public class AgendamentoController {

    private final AgendamentoService agendamentoService;
    private final AgendamentoStatusLifecycle agendamentoStatusLifecycle;
    private final DisponibilidadeService disponibilidadeService;
    private final AgendamentoMapper agendamentoMapper;
    private final IndicacaoRepository indicacaoRepository;
    private final AgendamentoFotografoRepository agendamentoFotografoRepository;
    private final JsonMapper objectMapper;

    public AgendamentoController(AgendamentoService agendamentoService,
                                  AgendamentoStatusLifecycle agendamentoStatusLifecycle,
                                  DisponibilidadeService disponibilidadeService,
                                  AgendamentoMapper agendamentoMapper,
                                  IndicacaoRepository indicacaoRepository,
                                  AgendamentoFotografoRepository agendamentoFotografoRepository,
                                  JsonMapper objectMapper) {
        this.agendamentoService = agendamentoService;
        this.agendamentoStatusLifecycle = agendamentoStatusLifecycle;
        this.disponibilidadeService = disponibilidadeService;
        this.agendamentoMapper = agendamentoMapper;
        this.indicacaoRepository = indicacaoRepository;
        this.agendamentoFotografoRepository = agendamentoFotografoRepository;
        this.objectMapper = objectMapper;
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
            @RequestParam(required = false) String clienteId,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String telefone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String cpf,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String origem,
            @RequestParam String pacoteId,
            @RequestParam(required = false) String editorId,
            @RequestParam(required = false) String dataHoraEnsaio,
            @RequestParam(required = false) String data,
            @RequestParam(required = false) String hora,
            @RequestParam(required = false, defaultValue = "60") String duracaoMinutos,
            @RequestParam String localEnsaio,
            @RequestParam(required = false) String enderecoCompleto,
            @RequestParam(required = false) String taxaDeslocamento,
            @RequestParam(required = false) String custoDeslocamento,
            @RequestParam(required = false) String repassarDeslocamento,
            @RequestParam(required = false) MultipartFile comprovanteEntrada,
            @RequestParam(required = false) String autorizaUsoImagem,
            @RequestParam(required = false) String clausulasPersonalizadas,
            @RequestParam(required = false) String observacoes,
            @RequestParam(required = false) String indicadorId,
            @RequestParam(required = false) String indicadorNome,
            @RequestParam(required = false) String indicadorTelefone,
            @RequestParam(required = false) String fotografoId,
            @RequestParam(required = false) String valorRepassarFotografo,
            @RequestParam(required = false) String fotografos
    ) {
        validarComprovante(comprovanteEntrada);

        var parsedPacoteId = UUID.fromString(pacoteId);
        var parsedEditorId = editorId != null && !editorId.isBlank() ? UUID.fromString(editorId) : null;
        var parsedClienteId = clienteId != null && !clienteId.isBlank() ? UUID.fromString(clienteId) : null;
        var parsedDuracao = duracaoMinutos != null ? Integer.parseInt(duracaoMinutos) : 60;
        var parsedTaxa = taxaDeslocamento != null && !taxaDeslocamento.isBlank() ? new BigDecimal(taxaDeslocamento) : BigDecimal.ZERO;
        var parsedCusto = custoDeslocamento != null && !custoDeslocamento.isBlank() ? new BigDecimal(custoDeslocamento) : BigDecimal.ZERO;
        var parsedRepassar = repassarDeslocamento == null || "true".equalsIgnoreCase(repassarDeslocamento);
        var parsedAutoriza = autorizaUsoImagem != null && "true".equalsIgnoreCase(autorizaUsoImagem);

        LocalDateTime parsedDataHora = null;
        if (dataHoraEnsaio != null && !dataHoraEnsaio.isBlank()) {
            parsedDataHora = LocalDateTime.parse(dataHoraEnsaio, DateTimeFormatter.ISO_DATE_TIME);
        } else if (data != null && !data.isBlank() && hora != null && !hora.isBlank()) {
            parsedDataHora = LocalDateTime.of(
                LocalDate.parse(data, DateTimeFormatter.ISO_DATE),
                LocalTime.parse(hora, DateTimeFormatter.ofPattern("HH:mm"))
            );
        }

        var parsedIndicadorId = indicadorId != null && !indicadorId.isBlank() ? UUID.fromString(indicadorId) : null;

        List<CriarAgendamentoCommand.FotografoRepasse> fotografosList = null;
        if (fotografos != null && !fotografos.isBlank()) {
            try {
                fotografosList = objectMapper.readValue(fotografos,
                    new TypeReference<List<CriarAgendamentoCommand.FotografoRepasse>>() {});
            } catch (Exception e) {
                throw new IllegalArgumentException("Formato inválido para o campo 'fotografos'. Use JSON array.", e);
            }
        } else if (fotografoId != null && !fotografoId.isBlank()) {
            var parsedFotografoId = UUID.fromString(fotografoId);
            var parsedValorRepassar = valorRepassarFotografo != null && !valorRepassarFotografo.isBlank()
                ? new BigDecimal(valorRepassarFotografo) : BigDecimal.ZERO;
            fotografosList = List.of(new CriarAgendamentoCommand.FotografoRepasse(parsedFotografoId, parsedValorRepassar));
        }

        var command = new CriarAgendamentoCommand(
            parsedClienteId, nome, telefone, email, cpf, cidade, estado, origem,
            parsedPacoteId, parsedEditorId, parsedDataHora, null, null, parsedDuracao,
            localEnsaio, enderecoCompleto, parsedTaxa, parsedCusto, parsedRepassar,
            comprovanteEntrada, parsedAutoriza, clausulasPersonalizadas, observacoes,
            parsedIndicadorId, indicadorNome, indicadorTelefone,
            fotografosList
        );

        var agendamento = agendamentoService.criarAgendamento(command);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(agendamentoMapper.toResponse(agendamento, null, null, null, null));
    }

    @GetMapping
    @Operation(summary = "Listar agendamentos", description = "Retorna agendamentos com suporte a filtros")
    @ApiResponse(responseCode = "200", description = "Lista de agendamentos")
    public ResponseEntity<List<AgendamentoResponse>> listar(
            @RequestParam(required = false) @Parameter(description = "Filtrar por status") String status,
            @RequestParam(required = false) @Parameter(description = "Filtrar por editor") UUID editorId,
            @RequestParam(required = false) @Parameter(description = "Filtrar por fotógrafo") UUID fotografoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(description = "Data início") LocalDateTime dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(description = "Data fim") LocalDateTime dataFim,
            @RequestParam(required = false) @Parameter(description = "Buscar por nome do cliente") String search) {
        StatusAgendamento statusEnum = null;
        if (status != null && !status.isBlank()) {
            statusEnum = StatusAgendamento.valueOf(status);
        }
        var agendamentos = agendamentoService.listarTodos(editorId, fotografoId, statusEnum, dataInicio, dataFim, search).stream()
            .map(a -> agendamentoMapper.toResponse(a, null, null, null, null))
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
            @RequestParam(required = false) UUID excluirAgendamentoId,
            @RequestParam(defaultValue = "false") Boolean bloqueiaDiaInteiro) {
        return ResponseEntity.ok(disponibilidadeService.verificarDisponibilidade(data, hora, duracaoMinutos, excluirAgendamentoId, bloqueiaDiaInteiro));
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
        var links = agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(id);
        var indicacoes = indicacaoRepository.findAllByAgendamentoId(id);
        if (indicacoes.isEmpty()) {
            return ResponseEntity.ok(agendamentoMapper.toResponse(agendamento, links, null, null, null));
        }
        var primeira = indicacoes.getFirst();
        return ResponseEntity.ok(agendamentoMapper.toResponse(
            agendamento, links, primeira.getValorComissao(), primeira.getIndicadorNome(), primeira.getStatus().name()));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Atualizar status do agendamento")
    public ResponseEntity<AgendamentoResponse> atualizarStatus(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id,
            @RequestBody Map<String, String> body) {
        var status = body.get("status");
        var agendamento = agendamentoStatusLifecycle.atualizarStatus(id, status);
        return ResponseEntity.ok(agendamentoMapper.toResponse(agendamento, null, null, null, null));
    }

    @PatchMapping("/{id}/reagendar")
    @Operation(summary = "Reagendar ensaio", description = "Atualiza data/hora e redefine status para CONFIRMADO")
    public ResponseEntity<AgendamentoResponse> reagendar(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Parameter(description = "Nova data") LocalDate data,
            @RequestParam(required = false) @Parameter(description = "Novo horário (HH:mm)") String hora,
            @RequestParam(required = false) @Parameter(description = "Nova duração em minutos") Integer duracaoMinutos) {
        var agendamento = agendamentoStatusLifecycle.reagendar(id, data, hora, duracaoMinutos);
        return ResponseEntity.ok(agendamentoMapper.toResponse(agendamento, null, null, null, null));
    }

    @PatchMapping("/{id}/destaque")
    @Operation(summary = "Alternar destaque do ensaio")
    public ResponseEntity<AgendamentoResponse> toggleDestaque(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id) {
        var agendamento = agendamentoStatusLifecycle.toggleDestaque(id);
        return ResponseEntity.ok(agendamentoMapper.toResponse(agendamento, null, null, null, null));
    }

    @PostMapping("/{id}/pagamento-final")
    @Operation(summary = "Registrar pagamento final", description = "Registra o pagamento final com comprovante obrigatório e finaliza o ensaio")
    public ResponseEntity<AgendamentoResponse> registrarPagamentoFinal(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id,
            @RequestParam @Parameter(description = "Comprovante de pagamento final (obrigatório)") MultipartFile comprovanteFinal) {
        var agendamento = agendamentoStatusLifecycle.registrarPagamentoFinal(id, comprovanteFinal);
        return ResponseEntity.ok(agendamentoMapper.toResponse(agendamento, null, null, null, null));
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
