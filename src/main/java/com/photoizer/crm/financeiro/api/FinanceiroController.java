package com.photoizer.crm.financeiro.api;

import com.photoizer.crm.financeiro.model.FotoExtra;
import com.photoizer.crm.financeiro.model.Pagamento;
import com.photoizer.crm.financeiro.model.VideoExtra;
import com.photoizer.crm.financeiro.service.FinanceiroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/financeiro")
@Tag(name = "Financeiro", description = "Controle financeiro e fotos extras")
public class FinanceiroController {

    private final FinanceiroService financeiroService;

    public FinanceiroController(FinanceiroService financeiroService) {
        this.financeiroService = financeiroService;
    }

    @PostMapping("/agendamentos/{agendamentoId}/pagamentos")
    @Operation(summary = "Registrar pagamento")
    public ResponseEntity<Pagamento> registrarPagamento(
            @PathVariable UUID agendamentoId,
            @Valid @RequestBody Pagamento pagamento) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(financeiroService.registrarPagamento(agendamentoId, pagamento));
    }

    @PostMapping("/agendamentos/{agendamentoId}/fotos-extras")
    @Operation(summary = "Adicionar fotos extras (com comissão opcional)")
    public ResponseEntity<FotoExtra> adicionarFotoExtra(
            @PathVariable UUID agendamentoId,
            @RequestParam int quantidade,
            @RequestParam BigDecimal valorUnitario,
            @RequestParam(required = false) UUID indicadorId,
            @RequestParam(required = false) String indicadorNome,
            @RequestParam(required = false) String indicadorTelefone) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(financeiroService.adicionarFotoExtra(agendamentoId, quantidade, valorUnitario,
                indicadorNome, indicadorTelefone, indicadorId));
    }

    @PostMapping("/agendamentos/{agendamentoId}/videos-extras")
    @Operation(summary = "Adicionar vídeos extras (com comissão opcional)")
    public ResponseEntity<VideoExtra> adicionarVideoExtra(
            @PathVariable UUID agendamentoId,
            @RequestParam int quantidade,
            @RequestParam BigDecimal valorUnitario,
            @RequestParam(required = false) UUID indicadorId,
            @RequestParam(required = false) String indicadorNome,
            @RequestParam(required = false) String indicadorTelefone) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(financeiroService.adicionarVideoExtra(agendamentoId, quantidade, valorUnitario,
                indicadorNome, indicadorTelefone, indicadorId));
    }

    @GetMapping("/agendamentos/{agendamentoId}/pagamentos")
    @Operation(summary = "Listar pagamentos de um agendamento")
    public ResponseEntity<List<Pagamento>> listarPagamentos(@PathVariable UUID agendamentoId) {
        return ResponseEntity.ok(financeiroService.listarPagamentos(agendamentoId));
    }

    @PostMapping("/preview")
    @Operation(summary = "Calcular preview de valores financeiros")
    public ResponseEntity<FinanceiroPreviewResponse> preview(
            @RequestParam UUID pacoteId,
            @RequestParam(required = false) BigDecimal taxaDeslocamento) {
        return ResponseEntity.ok(financeiroService.calcularPreview(pacoteId, taxaDeslocamento));
    }

    @GetMapping("/resumo")
    @Operation(summary = "Obter resumo financeiro com totais agregados")
    public ResponseEntity<FinanceiroResumoResponse> resumo(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim) {
        var inicio = dataInicio != null ? dataInicio.atStartOfDay() : null;
        var fim = dataFim != null ? dataFim.atTime(LocalTime.MAX) : null;
        return ResponseEntity.ok(financeiroService.calcularResumo(inicio, fim));
    }

    @GetMapping("/relatorios")
    @Operation(summary = "Obter dados para relatorios financeiros")
    public ResponseEntity<FinanceiroRelatoriosResponse> relatorios(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim) {
        var inicio = dataInicio != null ? dataInicio.atStartOfDay() : null;
        var fim = dataFim != null ? dataFim.atTime(LocalTime.MAX) : null;
        return ResponseEntity.ok(financeiroService.calcularRelatorios(inicio, fim));
    }

    @GetMapping("/clientes/{clienteId}/bloqueado")
    @Operation(summary = "Verificar se cliente está bloqueado")
    public ResponseEntity<Boolean> isClienteBloqueado(@PathVariable UUID clienteId) {
        return ResponseEntity.ok(financeiroService.isClienteBloqueado(clienteId));
    }
}
