package com.photoizer.crm.financeiro.api;

import com.photoizer.crm.financeiro.model.StatusReceita;
import com.photoizer.crm.financeiro.model.TipoServico;
import com.photoizer.crm.financeiro.service.FinanceiroDashboardService;
import com.photoizer.crm.financeiro.service.FinanceiroQueryService;
import com.photoizer.crm.financeiro.service.FinanceiroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/financeiro")
@Tag(name = "Financeiro", description = "Controle financeiro e fotos extras")
public class FinanceiroController {

    private final FinanceiroService financeiroService;
    private final FinanceiroDashboardService financeiroDashboardService;
    private final FinanceiroQueryService financeiroQueryService;

    public FinanceiroController(FinanceiroService financeiroService,
                                FinanceiroDashboardService financeiroDashboardService,
                                FinanceiroQueryService financeiroQueryService) {
        this.financeiroService = financeiroService;
        this.financeiroDashboardService = financeiroDashboardService;
        this.financeiroQueryService = financeiroQueryService;
    }

    @PostMapping("/agendamentos/{agendamentoId}/pagamentos")
    @Operation(summary = "Registrar pagamento")
    @RolesAllowed("ADMIN")
    public ResponseEntity<PagamentoResponse> registrarPagamento(
            @PathVariable UUID agendamentoId,
            @Valid @RequestBody PagamentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(PagamentoResponse.of(financeiroService.registrarPagamento(agendamentoId, request)));
    }

    @PostMapping("/agendamentos/{agendamentoId}/fotos-extras")
    @Operation(summary = "Adicionar fotos extras (com comissão opcional)")
    @RolesAllowed("ADMIN")
    public ResponseEntity<ExtraServicoResponse> adicionarFotoExtra(
            @PathVariable UUID agendamentoId,
            @RequestParam @Positive int quantidade,
            @RequestParam @Positive BigDecimal valorUnitario,
            @RequestParam(required = false) UUID indicadorId,
            @RequestParam(required = false) String indicadorNome,
            @RequestParam(required = false) String indicadorTelefone) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ExtraServicoResponse.of(financeiroService.adicionarFotoExtra(agendamentoId, quantidade, valorUnitario,
                indicadorNome, indicadorTelefone, indicadorId)));
    }

    @PostMapping("/agendamentos/{agendamentoId}/videos-extras")
    @Operation(summary = "Adicionar vídeos extras (com comissão opcional)")
    @RolesAllowed("ADMIN")
    public ResponseEntity<ExtraServicoResponse> adicionarVideoExtra(
            @PathVariable UUID agendamentoId,
            @RequestParam @Positive int quantidade,
            @RequestParam @Positive BigDecimal valorUnitario,
            @RequestParam(required = false) UUID indicadorId,
            @RequestParam(required = false) String indicadorNome,
            @RequestParam(required = false) String indicadorTelefone) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ExtraServicoResponse.of(financeiroService.adicionarVideoExtra(agendamentoId, quantidade, valorUnitario,
                indicadorNome, indicadorTelefone, indicadorId)));
    }

    @GetMapping("/agendamentos/{agendamentoId}/pagamentos")
    @Operation(summary = "Listar pagamentos de um agendamento")
    public ResponseEntity<List<PagamentoResponse>> listarPagamentos(@PathVariable UUID agendamentoId) {
        return ResponseEntity.ok(financeiroService.listarPagamentos(agendamentoId));
    }

    @GetMapping("/agendamentos/{agendamentoId}/financeiro")
    @Operation(summary = "Resumo financeiro do trabalho (valores, despesas vinculadas, lucro e margem)")
    public ResponseEntity<FinanceiroTrabalhoResponse> resumoFinanceiroTrabalho(@PathVariable UUID agendamentoId) {
        return ResponseEntity.ok(financeiroService.resumoPorAgendamento(agendamentoId));
    }

    @PostMapping("/preview")
    @Operation(summary = "Calcular preview de valores financeiros")
    public ResponseEntity<FinanceiroPreviewResponse> preview(
            @RequestParam UUID pacoteId,
            @RequestParam(required = false) BigDecimal taxaDeslocamento) {
        return ResponseEntity.ok(financeiroService.calcularPreview(pacoteId, taxaDeslocamento));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard financeiro agregado (cards, gráficos e últimos lançamentos)")
    public ResponseEntity<FinanceiroDashboardResponse> dashboard(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @RequestParam(required = false) TipoServico tipoServico,
            @RequestParam(required = false) StatusReceita status,
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) String formaPagamento) {
        return ResponseEntity.ok(financeiroDashboardService.calcular(
            dataInicio, dataFim, tipoServico, status, clienteId, formaPagamento));
    }

    @GetMapping("/fluxo-caixa")
    @Operation(summary = "Fluxo de caixa projetado (entradas/saídas previstas por período)")
    public ResponseEntity<FluxoCaixaResponse> fluxoCaixa(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @RequestParam(required = false, defaultValue = "MENSAL") String visao) {
        return ResponseEntity.ok(financeiroQueryService.calcularFluxoCaixa(dataInicio, dataFim, visao));
    }

    @GetMapping("/resumo")
    @Operation(summary = "Obter resumo financeiro com totais agregados")
    public ResponseEntity<FinanceiroResumoResponse> resumo(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim) {
        var inicio = dataInicio != null ? dataInicio.atStartOfDay() : null;
        var fim = dataFim != null ? dataFim.plusDays(1).atStartOfDay() : null;
        return ResponseEntity.ok(financeiroService.calcularResumo(inicio, fim));
    }

    @GetMapping("/relatorios")
    @Operation(summary = "Obter dados para relatorios financeiros")
    public ResponseEntity<FinanceiroRelatoriosResponse> relatorios(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim) {
        var inicio = dataInicio != null ? dataInicio.atStartOfDay() : null;
        var fim = dataFim != null ? dataFim.plusDays(1).atStartOfDay() : null;
        return ResponseEntity.ok(financeiroService.calcularRelatorios(inicio, fim));
    }

    @GetMapping("/clientes/{clienteId}/bloqueado")
    @Operation(summary = "Verificar se cliente está bloqueado")
    public ResponseEntity<Boolean> isClienteBloqueado(@PathVariable UUID clienteId) {
        return ResponseEntity.ok(financeiroService.isClienteBloqueado(clienteId));
    }
}
