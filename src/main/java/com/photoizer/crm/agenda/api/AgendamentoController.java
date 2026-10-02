package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.agenda.service.AgendamentoService;
import com.photoizer.crm.agenda.service.AgendamentoStatusLifecycle;
import com.photoizer.crm.agenda.service.CriarPropostaCommand;
import com.photoizer.crm.agenda.service.DisponibilidadeService;
import com.photoizer.crm.comissao.repository.IndicacaoRepository;
import com.photoizer.crm.shared.exception.BadRequestException;
import com.photoizer.crm.shared.model.FormaPagamento;
import com.photoizer.crm.shared.storage.FileServeHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final FileServeHelper fileServeHelper;

    public AgendamentoController(AgendamentoService agendamentoService,
                                  AgendamentoStatusLifecycle agendamentoStatusLifecycle,
                                  DisponibilidadeService disponibilidadeService,
                                  AgendamentoMapper agendamentoMapper,
                                  IndicacaoRepository indicacaoRepository,
                                  AgendamentoFotografoRepository agendamentoFotografoRepository,
                                  FileServeHelper fileServeHelper) {
        this.agendamentoService = agendamentoService;
        this.agendamentoStatusLifecycle = agendamentoStatusLifecycle;
        this.disponibilidadeService = disponibilidadeService;
        this.agendamentoMapper = agendamentoMapper;
        this.indicacaoRepository = indicacaoRepository;
        this.agendamentoFotografoRepository = agendamentoFotografoRepository;
        this.fileServeHelper = fileServeHelper;
    }

    @PostMapping("/proposta")
    @Operation(summary = "Criar proposta/pré-reserva",
        description = "Cria uma pré-reserva (não ocupa agenda) e gera o link público de assinatura para o cliente")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Proposta criada com sucesso"),
        @ApiResponse(responseCode = "422", description = "Dados inválidos", content = @Content),
        @ApiResponse(responseCode = "409", description = "Conflito de agenda", content = @Content)
    })
    public ResponseEntity<AgendamentoResponse> criarProposta(
            @Valid @RequestBody CriarPropostaRequest request) {
        var fotografos = request.fotografos() == null ? List.<CriarPropostaCommand.FotografoRepasse>of()
            : request.fotografos().stream()
                .map(f -> new CriarPropostaCommand.FotografoRepasse(
                    f.fotografoId(), f.valorRepassar(), f.tipoValor(), f.percentual()))
                .toList();

        var command = new CriarPropostaCommand(
            request.pacoteId(), request.editorId(), request.fotografoId(), request.dataHoraEnsaio(),
            request.duracaoMinutos(), request.localEnsaio(),
            request.custoDeslocamento(), request.repassarDeslocamento(), request.clausulasPersonalizadas(),
            request.observacoes(), request.indicadorId(), request.indicadorNome(),
            request.indicadorTelefone(), fotografos
        );

        var agendamento = agendamentoService.criarProposta(command);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(agendamentoMapper.toResponse(agendamento, null, null, null, null));
    }

    @PatchMapping("/{id}/confirmar-pagamento")
    @RolesAllowed({"ADMIN", "FOTOGRAFO"})
    @Operation(summary = "Confirmar pagamento da reserva",
        description = "Staff confere o comprovante enviado pelo cliente; move a proposta para PAGAMENTO_CONFIRMADO")
    public ResponseEntity<AgendamentoResponse> confirmarPagamento(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id) {
        var agendamento = agendamentoService.confirmarPagamento(id);
        return ResponseEntity.ok(agendamentoMapper.toResponse(agendamento, null, null, null, null));
    }

    @PatchMapping("/{id}/aprovar")
    @RolesAllowed({"ADMIN", "FOTOGRAFO"})
    @Operation(summary = "Aprovar proposta",
        description = "Valida conflito de agenda e confirma o agendamento, que passa a ocupar a agenda")
    public ResponseEntity<AgendamentoResponse> aprovar(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id) {
        var agendamento = agendamentoService.aprovar(id);
        return ResponseEntity.ok(agendamentoMapper.toResponse(agendamento, null, null, null, null));
    }

    @PatchMapping("/{id}/recusar")
    @RolesAllowed({"ADMIN", "FOTOGRAFO"})
    @Operation(summary = "Recusar proposta",
        description = "Recusa a proposta em análise, registrando o motivo e o autor; move para CANCELADO")
    public ResponseEntity<AgendamentoResponse> recusarProposta(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id,
            @AuthenticationPrincipal String userIdStr,
            @Valid @RequestBody RecusarPropostaRequest request) {
        var autorId = userIdStr != null && !userIdStr.isBlank() ? UUID.fromString(userIdStr) : null;
        var agendamento = agendamentoService.recusar(id, request.motivo(), autorId);
        return ResponseEntity.ok(agendamentoMapper.toResponse(agendamento, null, null, null, null));
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
        var agendamentos = agendamentoService.listarTodos(editorId, fotografoId, statusEnum, dataInicio, dataFim, search);
        var fotografosPorAgendamento = agendamentos.isEmpty()
            ? Map.<UUID, List<AgendamentoFotografo>>of()
            : agendamentoFotografoRepository.findByAgendamentoIdInWithFotografo(
                    agendamentos.stream().map(a -> a.getId()).toList())
                .stream()
                .collect(Collectors.groupingBy(af -> af.getAgendamento().getId()));
        var response = agendamentos.stream()
            .map(a -> agendamentoMapper.toResponse(a,
                fotografosPorAgendamento.getOrDefault(a.getId(), List.of()), null, null, null))
            .toList();
        return ResponseEntity.ok(response);
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
            @RequestParam(defaultValue = "false") Boolean bloqueiaDiaInteiro,
            @RequestParam(required = false) UUID fotografoId) {
        return ResponseEntity.ok(disponibilidadeService.verificarDisponibilidade(data, hora, duracaoMinutos, excluirAgendamentoId, bloqueiaDiaInteiro, fotografoId));
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
            @Parameter(description = "Nova data") java.time.LocalDate data,
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

    @PatchMapping("/{id}/fotografo")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Transferir ensaio para outro fotógrafo",
        description = "Transfere o fotógrafo responsável do ensaio (somente ADMIN e status CONFIRMADO)")
    public ResponseEntity<AgendamentoResponse> reatribuirFotografo(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id,
            @AuthenticationPrincipal String userIdStr,
            @RequestBody @Valid ReatribuirFotografoRequest request) {
        var solicitanteId = userIdStr != null && !userIdStr.isBlank() ? UUID.fromString(userIdStr) : null;
        return ResponseEntity.ok(agendamentoService.reatribuirFotografo(
            id, request.fotografoId(), request.motivo(), solicitanteId));
    }

    @GetMapping("/{id}/reatribuicoes")
    @Operation(summary = "Listar histórico de transferências do agendamento")
    public ResponseEntity<List<ReatribuicaoResponse>> listarReatribuicoes(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id) {
        return ResponseEntity.ok(agendamentoService.listarReatribuicoes(id));
    }

    @GetMapping("/{id}/termo")
    @Operation(summary = "Baixar termo assinado (PDF)",
        description = "Serve o PDF do snapshot imutável assinado pelo cliente")
    public ResponseEntity<org.springframework.core.io.Resource> downloadTermo(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id) {
        var agendamento = agendamentoService.buscarPorId(id);
        if (agendamento.getUrlPdfAssinatura() == null) {
            return ResponseEntity.notFound().build();
        }
        return fileServeHelper.servirArquivo(
            agendamento.getUrlPdfAssinatura(), "termo_" + id + ".pdf", "inline");
    }

    @PostMapping("/{id}/pagamento-final")
    @Operation(summary = "Registrar pagamento final", description = "Registra o pagamento final com comprovante obrigatório e finaliza o ensaio")
    public ResponseEntity<AgendamentoResponse> registrarPagamentoFinal(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id,
            @RequestParam(required = false) @Parameter(description = "Comprovante de pagamento final (obrigatório, exceto em dinheiro)") MultipartFile comprovanteFinal,
            @RequestParam(required = false) @Parameter(description = "Forma de pagamento (ex.: DINHEIRO)") String formaPagamento) {
        var agendamento = agendamentoStatusLifecycle.registrarPagamentoFinal(
            id, comprovanteFinal, parseFormaPagamento(formaPagamento));
        return ResponseEntity.ok(agendamentoMapper.toResponse(agendamento, null, null, null, null));
    }

    private FormaPagamento parseFormaPagamento(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return FormaPagamento.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Forma de pagamento inválida: " + valor);
        }
    }
}
