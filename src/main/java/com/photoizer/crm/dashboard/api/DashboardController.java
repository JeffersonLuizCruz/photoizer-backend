package com.photoizer.crm.dashboard.api;

import com.photoizer.crm.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Métricas e gráficos do dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/financeiro-mensal")
    @Operation(summary = "Dados financeiros mensais para gráficos do dashboard")
    public ResponseEntity<DashboardMensalResponse> financeiroMensal(
            @RequestParam(defaultValue = "6") int meses) {
        return ResponseEntity.ok(dashboardService.calcularFinanceiroMensal(meses));
    }

    @GetMapping("/ecommerce")
    @Operation(summary = "Métricas consolidadas do ecommerce (total faturado, fotos, ticket médio, top clientes)")
    public ResponseEntity<DashboardEcommerceResponse> ecommerce() {
        return ResponseEntity.ok(dashboardService.calcularEcommerce());
    }

    @GetMapping("/ecommerce/mensal")
    @Operation(summary = "Série histórica mensal de vendas do ecommerce")
    public ResponseEntity<DashboardEcommerceMensalResponse> ecommerceMensal(
            @RequestParam(defaultValue = "6") int meses) {
        return ResponseEntity.ok(dashboardService.calcularEcommerceMensal(meses));
    }

    @GetMapping("/kpis")
    @Operation(summary = "KPIs do dashboard (agendamentos, receita, conversão, novos clientes, tarefas)")
    public ResponseEntity<DashboardKpisResponse> kpis() {
        return ResponseEntity.ok(dashboardService.calcularKpis());
    }
}
