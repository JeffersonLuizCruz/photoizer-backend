package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.model.CompraExtra;
import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Facade de leitura para métricas de e-commerce.
 * Pattern: Query Service Facade — centraliza agregações de vendas no módulo ecommerce,
 * evitando que dashboard acesse CompraExtraRepository diretamente com findAll().
 */
@Service
@Transactional(readOnly = true)
public class EcommerceQueryService {

    private final CompraExtraRepository compraExtraRepository;

    public EcommerceQueryService(CompraExtraRepository compraExtraRepository) {
        this.compraExtraRepository = compraExtraRepository;
    }

    /**
     * Retorna consolidado de vendas pagas: totais, ticket médio, e compras por agendamento.
     */
    public EcommerceConsolidado obterConsolidado() {
        var comprasPagas = compraExtraRepository.findByStatus(StatusCompraExtra.PAGA);

        var totalFaturado = comprasPagas.stream()
            .map(CompraExtra::getValorTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalFotosExtras = comprasPagas.stream()
            .filter(c -> c.getQuantidadeFotos() != null)
            .mapToInt(CompraExtra::getQuantidadeFotos)
            .sum();
        var totalCompras = comprasPagas.size();
        var ticketMedio = totalCompras > 0
            ? totalFaturado.divide(BigDecimal.valueOf(totalCompras), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        Map<UUID, CompraAgg> porAgendamento = new HashMap<>();
        for (var compra : comprasPagas) {
            porAgendamento.merge(compra.getAgendamentoId(),
                new CompraAgg(1, compra.getValorTotal()),
                (a, b) -> new CompraAgg(a.qtd + b.qtd, a.total.add(b.total)));
        }

        return new EcommerceConsolidado(totalCompras, totalFotosExtras, totalFaturado, ticketMedio, porAgendamento);
    }

    /**
     * Retorna histórico mensal de vendas no período informado.
     * Usa query filtrada por período em vez de findAll().
     */
    public List<DadosEcommerceMensal> obterHistoricoMensal(int meses) {
        var hoje = java.time.LocalDate.now();
        var mesAtual = YearMonth.from(hoje);
        var inicio = mesAtual.minusMonths(meses - 1).atDay(1).atStartOfDay();
        var fim = mesAtual.atEndOfMonth().atTime(23, 59, 59);

        var todasCompras = compraExtraRepository.findByPeriodo(inicio, fim,
            org.springframework.data.domain.Pageable.unpaged()).getContent();

        Map<YearMonth, List<CompraExtra>> porMes = new HashMap<>();
        for (var c : todasCompras) {
            if (c.getAuditInfo().getCreatedAt() == null) continue;
            var ym = YearMonth.from(c.getAuditInfo().getCreatedAt());
            porMes.computeIfAbsent(ym, k -> new ArrayList<>()).add(c);
        }

        var historico = new ArrayList<DadosEcommerceMensal>();
        for (int i = meses - 1; i >= 0; i--) {
            var ym = mesAtual.minusMonths(i);
            var compras = porMes.getOrDefault(ym, List.of());
            var qtdCompras = (int) compras.stream()
                .filter(c -> c.getStatus() == StatusCompraExtra.PAGA).count();
            var qtdFotos = compras.stream()
                .filter(c -> c.getStatus() == StatusCompraExtra.PAGA && c.getQuantidadeFotos() != null)
                .mapToInt(CompraExtra::getQuantidadeFotos).sum();
            var valorTotal = compras.stream()
                .filter(c -> c.getStatus() == StatusCompraExtra.PAGA)
                .map(CompraExtra::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            historico.add(new DadosEcommerceMensal(
                ym.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")),
                qtdCompras, qtdFotos, valorTotal));
        }
        return historico;
    }

    public record CompraAgg(int qtd, BigDecimal total) {}

    public record EcommerceConsolidado(
        int totalCompras,
        int totalFotosExtras,
        BigDecimal totalFaturado,
        BigDecimal ticketMedio,
        Map<UUID, CompraAgg> comprasPorAgendamento
    ) {}

    public record DadosEcommerceMensal(
        String mes,
        int quantidadeCompras,
        int quantidadeFotos,
        BigDecimal valorTotal
    ) {}
}
