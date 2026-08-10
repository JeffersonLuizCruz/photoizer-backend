package com.photoizer.crm.financeiro.api;

import com.photoizer.crm.financeiro.service.FinanceiroRelatorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/financeiro/relatorios")
@Tag(name = "Relatórios Financeiros", description = "Relatórios agregados para exportação")
public class FinanceiroRelatorioController {

    private final FinanceiroRelatorioService relatorioService;

    public FinanceiroRelatorioController(FinanceiroRelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    @GetMapping("/resumo-mensal")
    @Operation(summary = "Resumo financeiro mensal (receitas, despesas, lucro)")
    public ResponseEntity<ResumoMensalResponse> resumoMensal(
            @RequestParam LocalDate dataInicio,
            @RequestParam LocalDate dataFim) {
        return ResponseEntity.ok(relatorioService.resumoMensal(dataInicio, dataFim));
    }

    @GetMapping("/despesas-categoria")
    @Operation(summary = "Despesas por categoria com totais e percentuais")
    public ResponseEntity<DespesasCategoriaRelatorioResponse> despesasCategoria(
            @RequestParam LocalDate dataInicio,
            @RequestParam LocalDate dataFim) {
        return ResponseEntity.ok(relatorioService.despesasCategoria(dataInicio, dataFim));
    }

    @GetMapping("/inadimplencia")
    @Operation(summary = "Valores a receber vencidos")
    public ResponseEntity<InadimplenciaRelatorioResponse> inadimplencia(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim) {
        return ResponseEntity.ok(relatorioService.inadimplencia(dataInicio, dataFim));
    }

    @GetMapping("/rentabilidade-servico")
    @Operation(summary = "Rentabilidade por tipo de serviço")
    public ResponseEntity<List<FinanceiroDashboardResponse.RentabilidadeServico>> rentabilidadeServico(
            @RequestParam LocalDate dataInicio,
            @RequestParam LocalDate dataFim) {
        return ResponseEntity.ok(relatorioService.rentabilidadeServico(dataInicio, dataFim));
    }

    @GetMapping("/rentabilidade-cliente")
    @Operation(summary = "Rentabilidade por cliente")
    public ResponseEntity<RentabilidadeClienteResponse> rentabilidadeCliente(
            @RequestParam LocalDate dataInicio,
            @RequestParam LocalDate dataFim) {
        return ResponseEntity.ok(relatorioService.rentabilidadeCliente(dataInicio, dataFim));
    }

    @GetMapping("/comparativo")
    @Operation(summary = "Comparativo mensal ou anual")
    public ResponseEntity<ComparativoRelatorioResponse> comparativo(
            @RequestParam(defaultValue = "MENSAL") String tipo,
            @RequestParam LocalDate dataInicio,
            @RequestParam LocalDate dataFim) {
        return ResponseEntity.ok(relatorioService.comparativo(tipo, dataInicio, dataFim));
    }

    @GetMapping("/fiscal")
    @Operation(summary = "Relatório fiscal simplificado para contador")
    public ResponseEntity<RelatorioFiscalResponse> fiscal(
            @RequestParam LocalDate dataInicio,
            @RequestParam LocalDate dataFim) {
        return ResponseEntity.ok(relatorioService.fiscal(dataInicio, dataFim));
    }
}
