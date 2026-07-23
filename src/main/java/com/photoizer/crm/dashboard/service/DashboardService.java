package com.photoizer.crm.dashboard.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.model.StatusTarefa;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.agenda.repository.TarefaRepository;
import com.photoizer.crm.cliente.repository.ClienteRepository;
import com.photoizer.crm.comissao.model.Indicacao;
import com.photoizer.crm.comissao.repository.IndicacaoRepository;
import com.photoizer.crm.dashboard.api.DashboardEcommerceMensalResponse;
import com.photoizer.crm.dashboard.api.DashboardEcommerceMensalResponse.DadosEcommerceMensal;
import com.photoizer.crm.dashboard.api.DashboardEcommerceResponse;
import com.photoizer.crm.dashboard.api.DashboardEcommerceResponse.TopCliente;
import com.photoizer.crm.dashboard.api.DashboardKpisResponse;
import com.photoizer.crm.dashboard.api.DashboardMensalResponse;
import com.photoizer.crm.dashboard.api.DashboardMensalResponse.DadosMensais;
import com.photoizer.crm.dashboard.api.DashboardMensalResponse.ResumoMesAtual;
import com.photoizer.crm.despesa.repository.DespesaRepository;
import com.photoizer.crm.ecommerce.model.CompraExtra;
import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final Set<StatusAgendamento> STATUS_IGNORADOS = Set.of(
        StatusAgendamento.CANCELADO, StatusAgendamento.NO_SHOW
    );

    private static final Set<StatusAgendamento> STATUS_FINALIZADOS = Set.of(
        StatusAgendamento.EM_EDICAO,
        StatusAgendamento.SELECAO_DAS_FOTOS,
        StatusAgendamento.FOTOS_ENVIADAS_PARA_SELECAO,
        StatusAgendamento.FOTOS_ENTREGUES,
        StatusAgendamento.FINALIZADO
    );

    private final AgendamentoRepository agendamentoRepository;
    private final IndicacaoRepository indicacaoRepository;
    private final DespesaRepository despesaRepository;
    private final CompraExtraRepository compraExtraRepository;
    private final ClienteRepository clienteRepository;
    private final TarefaRepository tarefaRepository;

    public DashboardService(AgendamentoRepository agendamentoRepository,
                            IndicacaoRepository indicacaoRepository,
                            DespesaRepository despesaRepository,
                            CompraExtraRepository compraExtraRepository,
                            ClienteRepository clienteRepository,
                            TarefaRepository tarefaRepository) {
        this.agendamentoRepository = agendamentoRepository;
        this.indicacaoRepository = indicacaoRepository;
        this.despesaRepository = despesaRepository;
        this.compraExtraRepository = compraExtraRepository;
        this.clienteRepository = clienteRepository;
        this.tarefaRepository = tarefaRepository;
    }

    public DashboardMensalResponse calcularFinanceiroMensal(int mesesHistorico) {
        var hoje = LocalDate.now();
        var mesAtual = YearMonth.from(hoje);
        var meses = Math.max(mesesHistorico, 1);

        var inicio = mesAtual.minusMonths(meses - 1).atDay(1).atStartOfDay();
        var fim = mesAtual.atEndOfMonth().atTime(23, 59, 59, 999999999);

        var agendamentos = agendamentoRepository.findByDataBetween(inicio, fim, List.copyOf(STATUS_IGNORADOS));

        Map<YearMonth, List<Agendamento>> porMes = new TreeMap<>();
        for (var a : agendamentos) {
            var ym = YearMonth.from(a.getDataHoraEnsaio());
            if (ym.isBefore(mesAtual.minusMonths(meses - 1)) || ym.isAfter(mesAtual)) continue;
            porMes.computeIfAbsent(ym, k -> new ArrayList<>()).add(a);
        }

        var todosIds = agendamentos.stream().map(Agendamento::getId).toList();
        var indicacoes = indicacaoRepository.findByAgendamentoIdIn(todosIds);
        Map<UUID, BigDecimal> comissaoPorAgendamento = new HashMap<>();
        for (var ind : indicacoes) {
            comissaoPorAgendamento.put(ind.getAgendamentoId(), ind.getValorComissao());
        }

        var primeiroDia = inicio.toLocalDate();
        var ultimoDia = fim.toLocalDate();
        var todasDespesas = despesaRepository.findByDataBetweenOrderByDataDesc(primeiroDia, ultimoDia);
        Map<YearMonth, BigDecimal> despesasPorMes = new HashMap<>();
        for (var d : todasDespesas) {
            var ym = YearMonth.from(d.getData());
            despesasPorMes.merge(ym, d.getValor(), BigDecimal::add);
        }

        var historico = new ArrayList<DadosMensais>();
        ResumoMesAtual resumoMesAtual = null;

        for (int i = meses - 1; i >= 0; i--) {
            var ym = mesAtual.minusMonths(i);
            var lista = porMes.getOrDefault(ym, List.of());

            var valorConfirmados = BigDecimal.ZERO;
            var valorFinalizados = BigDecimal.ZERO;
            var deslocamento = BigDecimal.ZERO;
            var comissao = BigDecimal.ZERO;
            var entradasRecebidas = BigDecimal.ZERO;
            int qtdConfirmados = 0;
            int qtdFinalizados = 0;

            for (var a : lista) {
                var taxa = a.getTaxaDeslocamento() != null ? a.getTaxaDeslocamento() : BigDecimal.ZERO;
                deslocamento = deslocamento.add(taxa);

                var c = comissaoPorAgendamento.getOrDefault(a.getId(), BigDecimal.ZERO);
                comissao = comissao.add(c);

                if (STATUS_FINALIZADOS.contains(a.getStatus())) {
                    qtdFinalizados++;
                    valorFinalizados = valorFinalizados.add(a.getValorTotalFinal());
                }

                if (a.getStatus() == StatusAgendamento.CONFIRMADO
                    || a.getStatus() == StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL
                    || STATUS_FINALIZADOS.contains(a.getStatus())) {
                    qtdConfirmados++;
                    valorConfirmados = valorConfirmados.add(a.getValorTotalFinal());
                    entradasRecebidas = entradasRecebidas.add(
                        a.getValorEntradaPago() != null ? a.getValorEntradaPago() : BigDecimal.ZERO
                    );
                }
            }

            var despesasManuais = despesasPorMes.getOrDefault(ym, BigDecimal.ZERO);
            var totalDespesas = deslocamento.add(comissao).add(despesasManuais);

            var dados = new DadosMensais(
                ym.format(DateTimeFormatter.ofPattern("yyyy-MM")),
                lista.size(),
                valorConfirmados,
                valorFinalizados,
                deslocamento,
                comissao,
                despesasManuais,
                entradasRecebidas,
                entradasRecebidas.subtract(totalDespesas),
                valorConfirmados.subtract(totalDespesas)
            );
            historico.add(dados);

            if (ym.equals(mesAtual)) {
                var saldoRestante = valorConfirmados.subtract(entradasRecebidas);
                var saldoLiquido = entradasRecebidas.subtract(totalDespesas);
                var receitaProjetada = valorFinalizados.subtract(totalDespesas);
                var liquidoPrevisto = valorConfirmados.subtract(totalDespesas);

                resumoMesAtual = new ResumoMesAtual(
                    lista.size(),
                    qtdConfirmados,
                    valorConfirmados,
                    entradasRecebidas,
                    saldoRestante,
                    qtdFinalizados,
                    valorFinalizados,
                    deslocamento,
                    comissao,
                    despesasManuais,
                    saldoLiquido,
                    receitaProjetada,
                    saldoLiquido,
                    liquidoPrevisto
                );
            }
        }

        return new DashboardMensalResponse(resumoMesAtual, historico);
    }

    public DashboardEcommerceResponse calcularEcommerce() {
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

        var agendamentoIds = comprasPagas.stream()
            .map(CompraExtra::getAgendamentoId)
            .distinct()
            .toList();
        var agendamentos = agendamentoRepository.findAllById(agendamentoIds);

        Map<UUID, CompraAgg> porAgendamento = new HashMap<>();
        for (var compra : comprasPagas) {
            porAgendamento.merge(compra.getAgendamentoId(),
                new CompraAgg(1, compra.getValorTotal()),
                (a, b) -> new CompraAgg(a.qtd + b.qtd, a.total.add(b.total)));
        }

        var topClientes = agendamentos.stream()
            .map(a -> {
                var agg = porAgendamento.getOrDefault(a.getId(), new CompraAgg(0, BigDecimal.ZERO));
                return new TopCliente(
                    a.getCliente().getNome(),
                    a.getCliente().getTelefone(),
                    agg.qtd,
                    agg.total
                );
            })
            .sorted((c1, c2) -> c2.totalGasto().compareTo(c1.totalGasto()))
            .limit(5)
            .toList();

        return new DashboardEcommerceResponse(
            totalCompras, (int) totalFotosExtras, totalFaturado, ticketMedio, topClientes
        );
    }

    public DashboardEcommerceMensalResponse calcularEcommerceMensal(int meses) {
        var hoje = LocalDate.now();
        var mesAtual = YearMonth.from(hoje);
        var inicio = mesAtual.minusMonths(meses - 1).atDay(1).atStartOfDay();
        var fim = mesAtual.atEndOfMonth().atTime(23, 59, 59);

        var todasCompras = compraExtraRepository.findAll().stream()
            .filter(c -> c.getCreatedAt() != null
                && !c.getCreatedAt().isBefore(inicio)
                && !c.getCreatedAt().isAfter(fim))
            .toList();

        Map<YearMonth, List<CompraExtra>> porMes = new TreeMap<>();
        for (var c : todasCompras) {
            var ym = YearMonth.from(c.getCreatedAt());
            porMes.computeIfAbsent(ym, k -> new ArrayList<>()).add(c);
        }

        var historico = new ArrayList<DadosEcommerceMensal>();
        for (int i = meses - 1; i >= 0; i--) {
            var ym = mesAtual.minusMonths(i);
            var compras = porMes.getOrDefault(ym, List.of());
            var qtdCompras = (int) compras.stream().filter(c -> c.getStatus() == StatusCompraExtra.PAGA).count();
            var qtdFotos = compras.stream()
                .filter(c -> c.getStatus() == StatusCompraExtra.PAGA && c.getQuantidadeFotos() != null)
                .mapToInt(CompraExtra::getQuantidadeFotos)
                .sum();
            var valorTotal = compras.stream()
                .filter(c -> c.getStatus() == StatusCompraExtra.PAGA)
                .map(CompraExtra::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            historico.add(new DadosEcommerceMensal(
                ym.format(DateTimeFormatter.ofPattern("yyyy-MM")),
                qtdCompras, qtdFotos, valorTotal
            ));
        }

        return new DashboardEcommerceMensalResponse(historico);
    }

    public DashboardKpisResponse calcularKpis() {
        var hoje = LocalDate.now();
        var inicioMes = hoje.withDayOfMonth(1).atStartOfDay();
        var fimMes = YearMonth.from(hoje).atEndOfMonth().atTime(23, 59, 59);

        var agendamentosMes = agendamentoRepository.countByDataHoraEnsaioBetween(inicioMes, fimMes);
        var agendamentosHoje = agendamentoRepository.countByDataHoraEnsaioBetween(
            hoje.atStartOfDay(), hoje.atTime(23, 59, 59));

        var receitaMes = agendamentoRepository.findAll().stream()
            .filter(a -> a.getDataHoraEnsaio() != null
                && !a.getDataHoraEnsaio().isBefore(inicioMes)
                && !a.getDataHoraEnsaio().isAfter(fimMes))
            .filter(a -> a.getStatus() == StatusAgendamento.CONFIRMADO
                || a.getStatus() == StatusAgendamento.REALIZADO
                || a.getStatus() == StatusAgendamento.EM_EDICAO
                || a.getStatus() == StatusAgendamento.FOTOS_ENVIADAS_PARA_SELECAO
                || a.getStatus() == StatusAgendamento.FOTOS_ENTREGUES
                || a.getStatus() == StatusAgendamento.FINALIZADO)
            .map(a -> a.getValorTotalFinal() != null ? a.getValorTotalFinal() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var novosClientesMes = clienteRepository.countByDataCadastroBetween(inicioMes, fimMes);

        var tarefasPendentes = tarefaRepository.findAll().stream()
            .filter(t -> t.getStatus() == StatusTarefa.PENDENTE)
            .count();

        var totalAgendamentos = agendamentoRepository.count();
        var taxaConversao = totalAgendamentos > 0
            ? (double) agendamentosMes / totalAgendamentos
            : 0.0;

        return new DashboardKpisResponse(
            agendamentosMes, receitaMes, taxaConversao,
            novosClientesMes, tarefasPendentes, agendamentosHoje
        );
    }

    private record CompraAgg(int qtd, BigDecimal total) {}
}
