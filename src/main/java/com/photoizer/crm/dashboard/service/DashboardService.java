package com.photoizer.crm.dashboard.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
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
import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.despesa.model.StatusDespesa;
import com.photoizer.crm.despesa.repository.DespesaRepository;
import com.photoizer.crm.ecommerce.model.CompraExtra;
import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import com.photoizer.crm.financeiro.model.Receita;
import com.photoizer.crm.financeiro.model.StatusReceita;
import com.photoizer.crm.financeiro.repository.ReceitaRepository;
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

    private static final com.photoizer.crm.comissao.model.StatusIndicacao STATUS_COMISSAO_CANCELADA = com.photoizer.crm.comissao.model.StatusIndicacao.CANCELADA;
    private static final com.photoizer.crm.comissao.model.StatusIndicacao STATUS_COMISSAO_PAGA = com.photoizer.crm.comissao.model.StatusIndicacao.PAGA;

    private final AgendamentoRepository agendamentoRepository;
    private final AgendamentoFotografoRepository agendamentoFotografoRepository;
    private final IndicacaoRepository indicacaoRepository;
    private final DespesaRepository despesaRepository;
    private final CompraExtraRepository compraExtraRepository;
    private final ClienteRepository clienteRepository;
    private final ReceitaRepository receitaRepository;

    public DashboardService(AgendamentoRepository agendamentoRepository,
                            AgendamentoFotografoRepository agendamentoFotografoRepository,
                            IndicacaoRepository indicacaoRepository,
                            DespesaRepository despesaRepository,
                            CompraExtraRepository compraExtraRepository,
                            ClienteRepository clienteRepository,
                            ReceitaRepository receitaRepository) {
        this.agendamentoRepository = agendamentoRepository;
        this.agendamentoFotografoRepository = agendamentoFotografoRepository;
        this.indicacaoRepository = indicacaoRepository;
        this.despesaRepository = despesaRepository;
        this.compraExtraRepository = compraExtraRepository;
        this.clienteRepository = clienteRepository;
        this.receitaRepository = receitaRepository;
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
        Map<UUID, BigDecimal> comissaoPagaPorAgendamento = new HashMap<>();
        for (var ind : indicacoes) {
            if (ind.getStatus() == STATUS_COMISSAO_CANCELADA) continue;
            comissaoPorAgendamento.put(ind.getAgendamentoId(), ind.getValorComissao());
            if (ind.getStatus() == STATUS_COMISSAO_PAGA) {
                comissaoPagaPorAgendamento.put(ind.getAgendamentoId(), ind.getValorComissao());
            }
        }

        var primeiroDia = inicio.toLocalDate();
        var ultimoDia = fim.toLocalDate();
        var todasDespesas = despesaRepository.findByDataBetweenOrderByDataDesc(primeiroDia, ultimoDia);
        Map<YearMonth, BigDecimal> despesasPorMes = new HashMap<>();
        Map<YearMonth, BigDecimal> despesasPagasPorMes = new HashMap<>();
        for (var d : todasDespesas) {
            var ym = YearMonth.from(d.getData());
            despesasPorMes.merge(ym, d.getValor(), BigDecimal::add);
            if (d.getStatus() == StatusDespesa.PAGO) {
                despesasPagasPorMes.merge(ym, d.getValor(), BigDecimal::add);
            }
        }

        var repasses = carregarRepasses();

        var receitasAvulsas = receitaRepository.findAll().stream()
            .filter(r -> r.getAgendamentoId() == null)
            .toList();

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
                var desloc = deslocamentoEfetivo(a);
                deslocamentoEfetivo = deslocamentoEfetivo.add(desloc);
                if (a.getValorRestante() != null && a.getValorRestante().compareTo(BigDecimal.ZERO) <= 0) {
                    deslocamentoEfetivoPago = deslocamentoEfetivoPago.add(desloc);
                }

                comissao = comissao.add(comissaoPorAgendamento.getOrDefault(a.getId(), BigDecimal.ZERO));
                comissaoPaga = comissaoPaga.add(comissaoPagaPorAgendamento.getOrDefault(a.getId(), BigDecimal.ZERO));

                repasse = repasse.add(repasses.previstos().getOrDefault(a.getId(), BigDecimal.ZERO));
                repassePago = repassePago.add(repasses.pagos().getOrDefault(a.getId(), BigDecimal.ZERO));

                if (STATUS_FINALIZADOS.contains(a.getStatus())) {
                    qtdFinalizados++;
                    valorFinalizados = valorFinalizados.add(a.getValorTotalFinal());
                }

                if (a.getStatus() == StatusAgendamento.CONFIRMADO
                    || a.getStatus() == StatusAgendamento.REALIZADO
                    || a.getStatus() == StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL
                    || STATUS_FINALIZADOS.contains(a.getStatus())) {
                    qtdConfirmados++;
                    valorEnsaiosConfirmados = valorEnsaiosConfirmados.add(a.getValorTotalFinal());
                    entradasRecebidas = entradasRecebidas.add(
                        a.getValorEntradaPago() != null ? a.getValorEntradaPago() : BigDecimal.ZERO
                    );
                }
            }

            var avulsasBruto = BigDecimal.ZERO;
            var avulsasRecebidas = BigDecimal.ZERO;
            for (var r : receitasAvulsas) {
                if (r.getStatus() == StatusReceita.CANCELADO) continue;
                if (r.getDataPrevisaoRecebimento() != null
                    && ym.equals(YearMonth.from(r.getDataPrevisaoRecebimento()))) {
                    avulsasBruto = avulsasBruto.add(r.getValorBruto());
                }
                if (r.getDataRecebimentoReal() != null
                    && ym.equals(YearMonth.from(r.getDataRecebimentoReal()))) {
                    avulsasRecebidas = avulsasRecebidas.add(r.getValorRecebido());
                }
            }

            var valorConfirmados = valorEnsaiosConfirmados.add(avulsasBruto);
            entradasRecebidas = entradasRecebidas.add(avulsasRecebidas);

            var despesasManuais = despesasPorMes.getOrDefault(ym, BigDecimal.ZERO);
            var despesasManuaisPagas = despesasPagasPorMes.getOrDefault(ym, BigDecimal.ZERO);
            var totalDespesas = deslocamentoEfetivo.add(comissao).add(repasse).add(despesasManuais);
            var totalDespesasPagas = deslocamentoEfetivoPago.add(comissaoPaga).add(repassePago).add(despesasManuaisPagas);

            var dados = new DadosMensais(
                ym.format(DateTimeFormatter.ofPattern("yyyy-MM")),
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

        var totalAgendamentos = agendamentoRepository.count();
        var taxaConversao = totalAgendamentos > 0
            ? (double) agendamentosMes / totalAgendamentos
            : 0.0;

        return new DashboardKpisResponse(
            agendamentosMes, receitaMes, taxaConversao,
            novosClientesMes, agendamentosHoje
        );
    }

    private BigDecimal deslocamentoEfetivo(Agendamento a) {
        if (Boolean.TRUE.equals(a.getRepassarDeslocamento())) return BigDecimal.ZERO;
        return a.getCustoDeslocamento() != null ? a.getCustoDeslocamento() : BigDecimal.ZERO;
    }

    private RepassesResumo carregarRepasses() {
        Map<UUID, BigDecimal> previstos = new HashMap<>();
        Map<UUID, BigDecimal> pagos = new HashMap<>();
        var linhas = agendamentoFotografoRepository.sumRepassesAtivosPorAgendamento(RepasseStatus.CANCELADO);
        for (var linha : linhas) {
            var agendamentoId = (UUID) linha[0];
            var status = (RepasseStatus) linha[1];
            var valor = (BigDecimal) linha[2];
            if (status == RepasseStatus.PAGO) {
                pagos.merge(agendamentoId, valor, BigDecimal::add);
            }
            previstos.merge(agendamentoId, valor, BigDecimal::add);
        }
        return new RepassesResumo(previstos, pagos);
    }

    private record RepassesResumo(Map<UUID, BigDecimal> previstos, Map<UUID, BigDecimal> pagos) {}

    private record CompraAgg(int qtd, BigDecimal total) {}
}
