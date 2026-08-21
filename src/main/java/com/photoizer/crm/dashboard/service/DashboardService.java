package com.photoizer.crm.dashboard.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.service.AgendamentoQueryService;
import com.photoizer.crm.cliente.service.ClienteQueryService;
import com.photoizer.crm.comissao.service.ComissaoQueryService;
import com.photoizer.crm.dashboard.api.DashboardEcommerceMensalResponse;
import com.photoizer.crm.dashboard.api.DashboardEcommerceMensalResponse.DadosEcommerceMensal;
import com.photoizer.crm.dashboard.api.DashboardEcommerceResponse;
import com.photoizer.crm.dashboard.api.DashboardEcommerceResponse.TopCliente;
import com.photoizer.crm.dashboard.api.DashboardKpisResponse;
import com.photoizer.crm.dashboard.api.DashboardMensalResponse;
import com.photoizer.crm.dashboard.api.DashboardMensalResponse.DadosMensais;
import com.photoizer.crm.dashboard.api.DashboardMensalResponse.ResumoMesAtual;
import com.photoizer.crm.despesa.service.DespesaQueryService;
import com.photoizer.crm.ecommerce.service.EcommerceQueryService;
import com.photoizer.crm.financeiro.service.ReceitaQueryService;
import com.photoizer.crm.shared.service.FinanceCalculator;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Orchestrator de métricas do dashboard.
 * Pattern: Orchestrator — compõe dados de 6 facades de módulos para produzir KPIs,
 * séries financeiras mensais e métricas de e-commerce. Sem acesso direto a repositórios.
 *
 * Refatorado: eliminação de findAll() em memória, remoção de 7 repositórios injetados,
 * uso de FinanceCalculator para cálculos compartilhados, cache para consultas pesadas.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final DateTimeFormatter MES_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AgendamentoQueryService agendamentoQueryService;
    private final ComissaoQueryService comissaoQueryService;
    private final DespesaQueryService despesaQueryService;
    private final ReceitaQueryService receitaQueryService;
    private final EcommerceQueryService ecommerceQueryService;
    private final ClienteQueryService clienteQueryService;
    private final FinanceCalculator financeCalculator;

    public DashboardService(AgendamentoQueryService agendamentoQueryService,
                            ComissaoQueryService comissaoQueryService,
                            DespesaQueryService despesaQueryService,
                            ReceitaQueryService receitaQueryService,
                            EcommerceQueryService ecommerceQueryService,
                            ClienteQueryService clienteQueryService,
                            FinanceCalculator financeCalculator) {
        this.agendamentoQueryService = agendamentoQueryService;
        this.comissaoQueryService = comissaoQueryService;
        this.despesaQueryService = despesaQueryService;
        this.receitaQueryService = receitaQueryService;
        this.ecommerceQueryService = ecommerceQueryService;
        this.clienteQueryService = clienteQueryService;
        this.financeCalculator = financeCalculator;
    }

    @Cacheable(value = "dashboard-financeiro", key = "#mesesHistorico")
    public DashboardMensalResponse calcularFinanceiroMensal(int mesesHistorico) {
        var hoje = LocalDate.now();
        var mesAtual = YearMonth.from(hoje);
        var meses = Math.max(mesesHistorico, 1);

        var inicio = mesAtual.minusMonths(meses - 1).atDay(1).atStartOfDay();
        var fim = mesAtual.atEndOfMonth().atTime(23, 59, 59, 999999999);
        var inicioLocalDate = inicio.toLocalDate();
        var fimLocalDate = fim.toLocalDate();

        var agendamentos = agendamentoQueryService.obterPorPeriodo(inicio, fim);
        var todosIds = agendamentos.stream().map(Agendamento::getId).toList();
        var comissaoPorAgendamento = comissaoQueryService.obterComissaoPorAgendamentos(todosIds);
        var despesas = despesaQueryService.obterPorPeriodo(inicioLocalDate, fimLocalDate);
        var repasses = financeCalculator.carregarRepasses(agendamentoQueryService.repasseRepository());
        var receitasAvulsas = receitaQueryService.obterAvulsasPorPeriodo(inicioLocalDate, fimLocalDate);

        Map<YearMonth, List<Agendamento>> porMes = new TreeMap<>();
        for (var a : agendamentos) {
            var ym = YearMonth.from(a.getDataHoraEnsaio());
            if (ym.isBefore(mesAtual.minusMonths(meses - 1)) || ym.isAfter(mesAtual)) continue;
            porMes.computeIfAbsent(ym, k -> new ArrayList<>()).add(a);
        }

        var historico = new ArrayList<DadosMensais>();
        ResumoMesAtual resumoMesAtual = null;

        for (int i = meses - 1; i >= 0; i--) {
            var ym = mesAtual.minusMonths(i);
            var lista = porMes.getOrDefault(ym, List.of());

            var valorEnsaiosConfirmados = BigDecimal.ZERO;
            var valorFinalizados = BigDecimal.ZERO;
            var deslocamentoEfetivo = BigDecimal.ZERO;
            var deslocamentoEfetivoPago = BigDecimal.ZERO;
            var comissao = BigDecimal.ZERO;
            var comissaoPaga = BigDecimal.ZERO;
            var repasse = BigDecimal.ZERO;
            var repassePago = BigDecimal.ZERO;
            var entradasRecebidas = BigDecimal.ZERO;
            int qtdConfirmados = 0;
            int qtdFinalizados = 0;

            for (var a : lista) {
                var desloc = financeCalculator.deslocamentoEfetivo(a);
                deslocamentoEfetivo = deslocamentoEfetivo.add(desloc);
                if (a.getValorRestante() != null && a.getValorRestante().compareTo(BigDecimal.ZERO) <= 0) {
                    deslocamentoEfetivoPago = deslocamentoEfetivoPago.add(desloc);
                }

                var comResumo = comissaoPorAgendamento.getOrDefault(a.getId(),
                    new ComissaoQueryService.ComissaoResumo(BigDecimal.ZERO, BigDecimal.ZERO));
                comissao = comissao.add(comResumo.total());
                comissaoPaga = comissaoPaga.add(comResumo.paga());

                repasse = repasse.add(repasses.previstos().getOrDefault(a.getId(), BigDecimal.ZERO));
                repassePago = repassePago.add(repasses.pagos().getOrDefault(a.getId(), BigDecimal.ZERO));

                if (financeCalculator.statusFinalizados().contains(a.getStatus())) {
                    qtdFinalizados++;
                    valorFinalizados = valorFinalizados.add(a.getValorTotalFinal());
                }

                if (financeCalculator.isConfirmadoOuFinalizado(a.getStatus())) {
                    qtdConfirmados++;
                    valorEnsaiosConfirmados = valorEnsaiosConfirmados.add(a.getValorTotalFinal());
                    entradasRecebidas = entradasRecebidas.add(
                        a.getValorEntradaPago() != null ? a.getValorEntradaPago() : BigDecimal.ZERO
                    );
                }
            }

            var avulsasBruto = receitasAvulsas.brutoPorMes().getOrDefault(ym, BigDecimal.ZERO);
            var avulsasRecebidas = receitasAvulsas.recebidoPorMes().getOrDefault(ym, BigDecimal.ZERO);

            var valorConfirmados = valorEnsaiosConfirmados.add(avulsasBruto);
            entradasRecebidas = entradasRecebidas.add(avulsasRecebidas);

            var despesasManuais = despesas.totalPorMes().getOrDefault(ym, BigDecimal.ZERO);
            var despesasManuaisPagas = despesas.pagasPorMes().getOrDefault(ym, BigDecimal.ZERO);
            var totalDespesas = deslocamentoEfetivo.add(comissao).add(repasse).add(despesasManuais);
            var totalDespesasPagas = deslocamentoEfetivoPago.add(comissaoPaga).add(repassePago).add(despesasManuaisPagas);

            var dados = new DadosMensais(
                ym.format(MES_FORMATTER),
                lista.size(),
                valorConfirmados,
                valorFinalizados,
                deslocamentoEfetivo,
                comissao,
                repasse,
                despesasManuais,
                entradasRecebidas,
                entradasRecebidas.subtract(totalDespesasPagas),
                valorConfirmados.subtract(totalDespesas)
            );
            historico.add(dados);

            if (ym.equals(mesAtual)) {
                var saldoRestante = valorConfirmados.subtract(entradasRecebidas);
                var saldoLiquido = entradasRecebidas.subtract(totalDespesas);
                var receitaProjetada = valorFinalizados.subtract(totalDespesas);

                resumoMesAtual = new ResumoMesAtual(
                    lista.size(),
                    qtdConfirmados,
                    valorConfirmados,
                    valorEnsaiosConfirmados,
                    entradasRecebidas,
                    saldoRestante,
                    qtdFinalizados,
                    valorFinalizados,
                    valorFinalizados,
                    deslocamentoEfetivo,
                    comissao,
                    repasse,
                    despesasManuais,
                    saldoLiquido,
                    receitaProjetada,
                    entradasRecebidas.subtract(totalDespesasPagas),
                    valorConfirmados.subtract(totalDespesas)
                );
            }
        }

        return new DashboardMensalResponse(resumoMesAtual, historico);
    }

    @Cacheable(value = "dashboard-ecommerce")
    public DashboardEcommerceResponse calcularEcommerce() {
        var consolidado = ecommerceQueryService.obterConsolidado();

        var agendamentoIds = consolidado.comprasPorAgendamento().keySet().stream().toList();
        var agendamentos = agendamentoQueryService.obterPorIds(agendamentoIds);

        var topClientes = agendamentos.stream()
            .map(a -> {
                var agg = consolidado.comprasPorAgendamento().getOrDefault(
                    a.getId(), new EcommerceQueryService.CompraAgg(0, BigDecimal.ZERO));
                return new TopCliente(
                    a.getCliente().getNome(),
                    a.getCliente().getTelefone(),
                    agg.qtd(),
                    agg.total()
                );
            })
            .sorted((c1, c2) -> c2.totalGasto().compareTo(c1.totalGasto()))
            .limit(5)
            .toList();

        return new DashboardEcommerceResponse(
            consolidado.totalCompras(), consolidado.totalFotosExtras(),
            consolidado.totalFaturado(), consolidado.ticketMedio(), topClientes
        );
    }

    @Cacheable(value = "dashboard-ecommerce-mensal", key = "#meses")
    public DashboardEcommerceMensalResponse calcularEcommerceMensal(int meses) {
        var historico = ecommerceQueryService.obterHistoricoMensal(meses).stream()
            .map(d -> new DadosEcommerceMensal(d.mes(), d.quantidadeCompras(), d.quantidadeFotos(), d.valorTotal()))
            .toList();
        return new DashboardEcommerceMensalResponse(historico);
    }

    @Cacheable(value = "dashboard-kpis")
    public DashboardKpisResponse calcularKpis() {
        var hoje = LocalDate.now();
        var inicioMes = hoje.withDayOfMonth(1).atStartOfDay();
        var fimMes = YearMonth.from(hoje).atEndOfMonth().atTime(23, 59, 59);

        var agendamentosMes = agendamentoQueryService.countPorPeriodo(inicioMes, fimMes);
        var agendamentosHoje = agendamentoQueryService.countPorPeriodo(
            hoje.atStartOfDay(), hoje.atTime(23, 59, 59));

        var receitaMes = agendamentoQueryService.calcularReceitaPeriodo(inicioMes, fimMes);

        var novosClientesMes = clienteQueryService.countNovosClientes(inicioMes, fimMes);

        var totalAgendamentos = agendamentoQueryService.countTotal();
        var taxaConversao = totalAgendamentos > 0
            ? (double) agendamentosMes / totalAgendamentos
            : 0.0;

        return new DashboardKpisResponse(
            agendamentosMes, receitaMes, taxaConversao,
            novosClientesMes, agendamentosHoje
        );
    }
}
