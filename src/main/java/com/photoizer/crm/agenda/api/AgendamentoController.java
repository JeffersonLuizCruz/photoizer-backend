package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.agenda.service.AgendamentoService;
import com.photoizer.crm.agenda.service.AgendamentoStatusLifecycle;
import com.photoizer.crm.agenda.service.CriarPropostaCommand;
import com.photoizer.crm.agenda.service.DisponibilidadeService;
import com.photoizer.crm.agenda.service.FotografoOpcoesService;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final FotografoOpcoesService fotografoOpcoesService;

    public AgendamentoController(AgendamentoService agendamentoService,
                                  AgendamentoStatusLifecycle agendamentoStatusLifecycle,
                                  DisponibilidadeService disponibilidadeService,
                                  AgendamentoMapper agendamentoMapper,
                                  IndicacaoRepository indicacaoRepository,
                                  AgendamentoFotografoRepository agendamentoFotografoRepository,
                                  FileServeHelper fileServeHelper,
                                  FotografoOpcoesService fotografoOpcoesService) {
        this.agendamentoService = agendamentoService;
        this.agendamentoStatusLifecycle = agendamentoStatusLifecycle;
        this.disponibilidadeService = disponibilidadeService;
        this.agendamentoMapper = agendamentoMapper;
        this.indicacaoRepository = indicacaoRepository;
        this.agendamentoFotografoRepository = agendamentoFotografoRepository;
        this.fileServeHelper = fileServeHelper;
        this.fotografoOpcoesService = fotografoOpcoesService;
    }

    @PostMapping("/proposta")
    @Operation(operationId = "criarProposta", summary = "Criar proposta/pré-reserva",
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
            request.custoDeslocamento(), request.valorEntradaPago(), request.repassarDeslocamento(),
            request.clausulasPersonalizadas(), request.observacoes(), request.indicadorId(),
            request.indicadorNome(), request.indicadorTelefone(), fotografos
        );

        var agendamento = agendamentoService.criarProposta(command);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(respostaPara(agendamentoMapper.toResponse(agendamento, null, null, null, null)));
    }

    @PutMapping("/{id}/proposta")
    @RolesAllowed({"ADMIN", "FOTOGRAFO"})
    @Operation(operationId = "atualizarProposta", summary = "Alterar proposta/pré-reserva",
        description = "Altera uma proposta ainda não assinada pelo cliente (status PRE_RESERVA). "
            + "Recalcula valores, revalida conflito de agenda e sincroniza repasses. "
            + "O link público permanece o mesmo.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Proposta alterada com sucesso"),
        @ApiResponse(responseCode = "404", description = "Proposta não encontrada", content = @Content),
        @ApiResponse(responseCode = "409", description = "Conflito de agenda", content = @Content),
        @ApiResponse(responseCode = "422", description = "Proposta não editável ou dados inválidos", content = @Content)
    })
    public ResponseEntity<AgendamentoResponse> atualizarProposta(
            @PathVariable @Parameter(description = "ID da proposta") UUID id,
            @Valid @RequestBody AtualizarPropostaRequest request) {
        var fotografos = request.fotografos() == null ? List.<CriarPropostaCommand.FotografoRepasse>of()
            : request.fotografos().stream()
                .map(f -> new CriarPropostaCommand.FotografoRepasse(
                    f.fotografoId(), f.valorRepassar(), f.tipoValor(), f.percentual()))
                .toList();

        var command = new CriarPropostaCommand(
            request.pacoteId(), request.editorId(), request.fotografoId(), request.dataHoraEnsaio(),
            request.duracaoMinutos(), request.localEnsaio(),
            request.custoDeslocamento(), request.valorEntradaPago(), request.repassarDeslocamento(),
            request.clausulasPersonalizadas(), request.observacoes(), request.indicadorId(),
            request.indicadorNome(), request.indicadorTelefone(), fotografos
        );

        return ResponseEntity.ok(respostaPara(agendamentoService.atualizarProposta(id, command)));
    }

    @DeleteMapping("/{id}/proposta")
    @RolesAllowed({"ADMIN", "FOTOGRAFO"})
    @Operation(operationId = "removerProposta", summary = "Remover proposta/pré-reserva",
        description = "Remove a proposta de forma auditável (soft-cancel): move para CANCELADO registrando "
            + "motivo e autor, preservando histórico e arquivos. Permitido apenas enquanto a proposta está em análise.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Proposta removida"),
        @ApiResponse(responseCode = "404", description = "Proposta não encontrada", content = @Content),
        @ApiResponse(responseCode = "422", description = "Proposta não removível", content = @Content)
    })
    public ResponseEntity<Void> removerProposta(
            @PathVariable @Parameter(description = "ID da proposta") UUID id,
            @AuthenticationPrincipal String userIdStr,
            @RequestParam(required = false) @Parameter(description = "Motivo da remoção") String motivo) {
        var autorId = userIdStr != null && !userIdStr.isBlank() ? UUID.fromString(userIdStr) : null;
        agendamentoService.removerProposta(id, motivo, autorId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/confirmar-pagamento")
    @RolesAllowed({"ADMIN", "FOTOGRAFO"})
    @Operation(operationId = "confirmarPagamentoReserva", summary = "Confirmar pagamento da reserva",
        description = "Staff confere o comprovante enviado pelo cliente; move a proposta para PAGAMENTO_CONFIRMADO")
    public ResponseEntity<AgendamentoResponse> confirmarPagamento(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id) {
        var agendamento = agendamentoService.confirmarPagamento(id);
        return ResponseEntity.ok(respostaPara(agendamentoMapper.toResponse(agendamento, null, null, null, null)));
    }

    @PatchMapping("/{id}/aprovar")
    @RolesAllowed({"ADMIN", "FOTOGRAFO"})
    @Operation(operationId = "aprovarProposta", summary = "Aprovar proposta",
        description = "Valida conflito de agenda e confirma o agendamento, que passa a ocupar a agenda")
    public ResponseEntity<AgendamentoResponse> aprovar(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id) {
        var agendamento = agendamentoService.aprovar(id);
        return ResponseEntity.ok(respostaPara(agendamentoMapper.toResponse(agendamento, null, null, null, null)));
    }

    @PatchMapping("/{id}/recusar")
    @RolesAllowed({"ADMIN", "FOTOGRAFO"})
    @Operation(operationId = "recusarProposta", summary = "Recusar proposta",
        description = "Recusa a proposta em análise, registrando o motivo e o autor; move para CANCELADO")
    public ResponseEntity<AgendamentoResponse> recusarProposta(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id,
            @AuthenticationPrincipal String userIdStr,
            @Valid @RequestBody RecusarPropostaRequest request) {
        var autorId = userIdStr != null && !userIdStr.isBlank() ? UUID.fromString(userIdStr) : null;
        var agendamento = agendamentoService.recusar(id, request.motivo(), autorId);
        return ResponseEntity.ok(respostaPara(agendamentoMapper.toResponse(agendamento, null, null, null, null)));
    }

    @GetMapping("/fotografos")
    @Operation(operationId = "listarFotografosOpcoes",
        summary = "Listar fotógrafos e o responsável padrão",
        description = "Retorna os fotógrafos ativos e o responsável pré-selecionado para novos agendamentos: "
            + "o próprio usuário logado quando ele é fotógrafo; caso contrário, o fotógrafo definido em Configurações.")
    @ApiResponse(responseCode = "200", description = "Fotógrafos disponíveis e responsável padrão")
    public ResponseEntity<OpcoesFotografoResponse> listarFotografos(
            @AuthenticationPrincipal String userIdStr) {
        var usuarioId = userIdStr != null && !userIdStr.isBlank() ? UUID.fromString(userIdStr) : null;
        return ResponseEntity.ok(fotografoOpcoesService.opcoes(usuarioId));
    }

    @GetMapping
    @Operation(operationId = "listarAgendamentos", summary = "Listar agendamentos", description = "Retorna agendamentos com suporte a filtros")
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
            .map(a -> respostaPara(agendamentoMapper.toResponse(a,
                fotografosPorAgendamento.getOrDefault(a.getId(), List.of()), null, null, null)))
            .toList();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(operationId = "atualizarAgendamento", summary = "Atualizar agendamento")
    public ResponseEntity<AgendamentoResponse> atualizar(
            @PathVariable UUID id,
            @RequestBody @Valid AtualizarAgendamentoRequest request) {
        return ResponseEntity.ok(respostaPara(agendamentoService.atualizar(id, request)));
    }

    @GetMapping("/verificar-disponibilidade")
    @Operation(operationId = "verificarDisponibilidade", summary = "Verificar disponibilidade de horário")
    public ResponseEntity<DisponibilidadeResponse> verificarDisponibilidade(
            @RequestParam @Parameter(description = "Data do ensaio (yyyy-MM-dd)") @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate data,
            @RequestParam @Parameter(description = "Hora do ensaio (HH:mm)") String hora,
            @RequestParam(defaultValue = "60") @Parameter(description = "Duração em minutos") Integer duracaoMinutos,
            @RequestParam(required = false) @Parameter(description = "Agendamento a desconsiderar (edição)") UUID excluirAgendamentoId,
            @RequestParam(defaultValue = "false") @Parameter(description = "Bloqueia o dia inteiro") Boolean bloqueiaDiaInteiro,
            @RequestParam(required = false) @Parameter(description = "Filtrar por fotógrafo") UUID fotografoId) {
        return ResponseEntity.ok(disponibilidadeService.verificarDisponibilidade(data, hora, duracaoMinutos, excluirAgendamentoId, bloqueiaDiaInteiro, fotografoId));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "buscarAgendamentoPorId", summary = "Buscar agendamento por ID", description = "Retorna os detalhes de um agendamento específico")
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
            return ResponseEntity.ok(respostaPara(agendamentoMapper.toResponse(agendamento, links, null, null, null)));
        }
        var primeira = indicacoes.getFirst();
        return ResponseEntity.ok(respostaPara(agendamentoMapper.toResponse(
            agendamento, links, primeira.getValorComissao(), primeira.getIndicadorNome(), primeira.getStatus().name())));
    }

    @PatchMapping("/{id}/status")
    @Operation(operationId = "atualizarStatusAgendamento", summary = "Atualizar status do agendamento")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status atualizado"),
        @ApiResponse(responseCode = "404", description = "Agendamento não encontrado", content = @Content),
        @ApiResponse(responseCode = "422", description = "Status inválido", content = @Content)
    })
    public ResponseEntity<AgendamentoResponse> atualizarStatus(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id,
            @Valid @RequestBody AtualizarStatusRequest body) {
        var agendamento = agendamentoStatusLifecycle.atualizarStatus(id, body.status());
        return ResponseEntity.ok(respostaPara(agendamentoMapper.toResponse(agendamento, null, null, null, null)));
    }

    @PatchMapping("/{id}/reagendar")
    @Operation(operationId = "reagendarEnsaio", summary = "Reagendar ensaio", description = "Atualiza data/hora e redefine status para CONFIRMADO")
    public ResponseEntity<AgendamentoResponse> reagendar(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Parameter(description = "Nova data") java.time.LocalDate data,
            @RequestParam(required = false) @Parameter(description = "Novo horário (HH:mm)") String hora,
            @RequestParam(required = false) @Parameter(description = "Nova duração em minutos") Integer duracaoMinutos) {
        var agendamento = agendamentoStatusLifecycle.reagendar(id, data, hora, duracaoMinutos);
        return ResponseEntity.ok(respostaPara(agendamentoMapper.toResponse(agendamento, null, null, null, null)));
    }

    @PatchMapping("/{id}/destaque")
    @Operation(operationId = "toggleDestaqueEnsaio", summary = "Alternar destaque do ensaio")
    public ResponseEntity<AgendamentoResponse> toggleDestaque(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id) {
        var agendamento = agendamentoStatusLifecycle.toggleDestaque(id);
        return ResponseEntity.ok(respostaPara(agendamentoMapper.toResponse(agendamento, null, null, null, null)));
    }

    @PatchMapping("/{id}/fotografo")
    @RolesAllowed("ADMIN")
    @Operation(operationId = "reatribuirFotografo", summary = "Transferir ensaio para outro fotógrafo",
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
    @Operation(operationId = "listarReatribuicoes", summary = "Listar histórico de transferências do agendamento")
    public ResponseEntity<List<ReatribuicaoResponse>> listarReatribuicoes(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id) {
        return ResponseEntity.ok(agendamentoService.listarReatribuicoes(id));
    }

    @GetMapping("/{id}/termo")
    @Operation(operationId = "baixarTermoAssinado", summary = "Baixar termo assinado (PDF)",
        description = "Serve o PDF do snapshot imutável assinado pelo cliente")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "PDF do termo",
            content = @Content(mediaType = "application/pdf")),
        @ApiResponse(responseCode = "404", description = "Termo não disponível", content = @Content)
    })
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
    @Operation(operationId = "registrarPagamentoFinal", summary = "Registrar pagamento final", description = "Registra o pagamento final (comprovante opcional) e finaliza o ensaio")
    public ResponseEntity<AgendamentoResponse> registrarPagamentoFinal(
            @PathVariable @Parameter(description = "ID do agendamento") UUID id,
            @RequestParam(required = false) @Parameter(description = "Comprovante de pagamento final (opcional)") MultipartFile comprovanteFinal,
            @RequestParam(required = false) @Parameter(description = "Forma de pagamento (ex.: DINHEIRO)") String formaPagamento) {
        var agendamento = agendamentoStatusLifecycle.registrarPagamentoFinal(
            id, comprovanteFinal, parseFormaPagamento(formaPagamento));
        return ResponseEntity.ok(respostaPara(agendamentoMapper.toResponse(agendamento, null, null, null, null)));
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

    /**
     * Aplica a política de visibilidade financeira ao retorno. O AGENDADOR não
     * tem acesso às métricas internas do estúdio (custos, comissões, partilha,
     * lucro e repasses); os demais papéis recebem a resposta completa.
     */
    private AgendamentoResponse respostaPara(AgendamentoResponse response) {
        return visualizadorSemMetricasInternas() ? response.ocultarDadosInternos() : response;
    }

    private boolean visualizadorSemMetricasInternas() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .map(org.springframework.security.core.GrantedAuthority::getAuthority)
            .anyMatch("ROLE_AGENDADOR"::equals);
    }
}
