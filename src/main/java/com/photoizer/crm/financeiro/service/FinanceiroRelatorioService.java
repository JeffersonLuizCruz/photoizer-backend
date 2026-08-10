package com.photoizer.crm.financeiro.service;

import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.despesa.model.StatusDespesa;
import com.photoizer.crm.despesa.repository.DespesaRepository;
import com.photoizer.crm.financeiro.api.ComparativoRelatorioResponse;
import com.photoizer.crm.financeiro.api.DespesasCategoriaRelatorioResponse;
import com.photoizer.crm.financeiro.api.FinanceiroDashboardResponse;
import com.photoizer.crm.financeiro.api.InadimplenciaRelatorioResponse;
import com.photoizer.crm.financeiro.api.RelatorioFiscalResponse;
import com.photoizer.crm.financeiro.api.RentabilidadeClienteResponse;
import com.photoizer.crm.financeiro.api.ResumoMensalResponse;
import com.photoizer.crm.financeiro.model.Receita;
import com.photoizer.crm.financeiro.model.StatusReceita;
import com.photoizer.crm.financeiro.model.TipoServico;
import com.photoizer.crm.financeiro.repository.ReceitaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FinanceiroRelatorioService {

    private final ReceitaRepository receitaRepository;
    private final DespesaRepository despesaRepository;

    public FinanceiroRelatorioService(ReceitaRepository receitaRepository,
                                      DespesaRepository despesaRepository) {
        this.receitaRepository = receitaRepository;
        this.despesaRepository = despesaRepository;
    }

    public ResumoMensalResponse resumoMensal(LocalDate inicio, LocalDate fim) {
        var receitas = receitasNoPeriodo(inicio, fim, false);
        var despesas = despesasNoPeriodo(inicio, fim);

        var receitasBrutas = BigDecimal.ZERO;
        var recebidas = BigDecimal.ZERO;
        var aReceber = BigDecimal.ZERO;
        long qtdReceitas = 0;
        for (var r : receitas) {
            receitasBrutas = receitasBrutas.add(r.getValorBruto());
            qtdReceitas++;
            if (r.getStatus() == StatusReceita.PAGO_TOTAL) {
                recebidas = recebidas.add(r.getValorRecebido());
            } else if (r.getStatus() == StatusReceita.PENDENTE || r.getStatus() == StatusReceita.PAGO_PARCIAL) {
                aReceber = aReceber.add(r.getValorFinal().subtract(r.getValorRecebido()));
            }
        }

        var despesasTotal = despesas.stream()
            .map(Despesa::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        var despesasPagas = despesas.stream()
            .filter(d -> d.getStatus() == StatusDespesa.PAGO)
            .map(Despesa::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        var aPagar = despesasTotal.subtract(despesasPagas);
        long qtdDespesas = despesas.size();

        var lucroPrevisto = receitasBrutas.subtract(despesasTotal);
        var lucroRealizado = recebidas.subtract(despesasPagas);
        var margemLucro = receitasBrutas.signum() > 0
            ? lucroPrevisto.multiply(BigDecimal.valueOf(100)).divide(receitasBrutas, 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return new ResumoMensalResponse(
            inicio, fim, receitasBrutas, recebidas, aReceber,
            despesasTotal, despesasPagas, aPagar,
            lucroPrevisto, lucroRealizado, margemLucro,
            qtdReceitas, qtdDespesas);
    }

    public DespesasCategoriaRelatorioResponse despesasCategoria(LocalDate inicio, LocalDate fim) {
        var despesas = despesasNoPeriodo(inicio, fim);

        Map<String, BigDecimal> valores = new LinkedHashMap<>();
        Map<String, Long> qtds = new LinkedHashMap<>();
        Map<String, String> cores = new LinkedHashMap<>();
        for (var d : despesas) {
            var nome = d.getCategoria() != null ? d.getCategoria() : "Outros";
            valores.merge(nome, d.getValor(), BigDecimal::add);
            qtds.merge(nome, 1L, Long::sum);
            var cor = d.getCategoriaRef() != null ? d.getCategoriaRef().getCor() : null;
            if (cor != null) cores.putIfAbsent(nome, cor);
        }

        var total = valores.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        var itens = valores.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .map(e -> {
                var valor = e.getValue();
                var percentual = total.signum() > 0
                    ? valor.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
                return new DespesasCategoriaRelatorioResponse.Item(
                    e.getKey(), cores.get(e.getKey()), valor, qtds.getOrDefault(e.getKey(), 0L), percentual);
            })
            .toList();
        return new DespesasCategoriaRelatorioResponse(total, itens);
    }

    public InadimplenciaRelatorioResponse inadimplencia(LocalDate dataInicio, LocalDate dataFim) {
        var hoje = LocalDate.now();
        var itens = new ArrayList<InadimplenciaRelatorioResponse.Item>();
        var receitas = receitaRepository.findAll();

        for (var r : receitas) {
            if (r.getStatus() != StatusReceita.PENDENTE && r.getStatus() != StatusReceita.PAGO_PARCIAL) continue;
            var vencimento = r.getDataPrevisaoRecebimento();
            if (vencimento == null || !vencimento.isBefore(hoje)) continue;
            if (dataInicio != null && vencimento.isBefore(dataInicio)) continue;
            if (dataFim != null && vencimento.isAfter(dataFim)) continue;

            var emAberto = r.getValorFinal().subtract(r.getValorRecebido());
            if (emAberto.signum() <= 0) continue;
            var diasAtraso = ChronoUnit.DAYS.between(vencimento, hoje);
            itens.add(new InadimplenciaRelatorioResponse.Item(
                r.getId(), r.getClienteNome(), r.getTipoServico(),
                r.getDescricao(), r.getValorFinal(), r.getValorRecebido(),
                emAberto, vencimento, diasAtraso));
        }

        itens.sort((a, b) -> Long.compare(b.diasAtraso(), a.diasAtraso()));
        var totalEmAberto = itens.stream()
            .map(InadimplenciaRelatorioResponse.Item::valorEmAberto)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new InadimplenciaRelatorioResponse(totalEmAberto, itens);
    }

    public List<FinanceiroDashboardResponse.RentabilidadeServico> rentabilidadeServico(LocalDate inicio, LocalDate fim) {
        var receitas = receitasNoPeriodo(inicio, fim, true);
        var receita = new HashMap<TipoServico, BigDecimal>();
        var liquido = new HashMap<TipoServico, BigDecimal>();
        for (var r : receitas) {
            receita.merge(r.getTipoServico(), r.getValorBruto(), BigDecimal::add);
            liquido.merge(r.getTipoServico(), r.getValorFinal(), BigDecimal::add);
        }
        return receita.entrySet().stream()
            .map(e -> {
                var l = liquido.getOrDefault(e.getKey(), BigDecimal.ZERO);
                var margem = e.getValue().signum() > 0
                    ? l.multiply(BigDecimal.valueOf(100)).divide(e.getValue(), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
                return new FinanceiroDashboardResponse.RentabilidadeServico(
                    e.getKey().name(), e.getValue(), l, margem);
            })
            .sorted((a, b) -> b.receita().compareTo(a.receita()))
            .toList();
    }

    public RentabilidadeClienteResponse rentabilidadeCliente(LocalDate inicio, LocalDate fim) {
        Map<UUID, List<Receita>> porCliente = new HashMap<>();
        for (var r : receitasNoPeriodo(inicio, fim, true)) {
            porCliente.computeIfAbsent(r.getClienteId(), k -> new ArrayList<>()).add(r);
        }

        var itens = porCliente.entrySet().stream()
            .map(e -> {
                var lista = e.getValue();
                var bruta = lista.stream().map(Receita::getValorBruto).reduce(BigDecimal.ZERO, BigDecimal::add);
                var liquida = lista.stream().map(Receita::getValorFinal).reduce(BigDecimal.ZERO, BigDecimal::add);
                var recebido = lista.stream().map(Receita::getValorRecebido).reduce(BigDecimal.ZERO, BigDecimal::add);
                var aReceber = lista.stream()
                    .filter(r -> r.getStatus() == StatusReceita.PENDENTE || r.getStatus() == StatusReceita.PAGO_PARCIAL)
                    .map(r -> r.getValorFinal().subtract(r.getValorRecebido()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                var margem = bruta.signum() > 0
                    ? liquida.multiply(BigDecimal.valueOf(100)).divide(bruta, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
                return new RentabilidadeClienteResponse.Item(
                    e.getKey(), lista.get(0).getClienteNome(), bruta, liquida, recebido,
                    aReceber, lista.size(), margem);
            })
            .sorted((a, b) -> b.receitaBruta().compareTo(a.receitaBruta()))
            .toList();
        return new RentabilidadeClienteResponse(itens);
    }

    public ComparativoRelatorioResponse comparativo(String tipo, LocalDate inicio, LocalDate fim) {
        boolean anual = "ANUAL".equalsIgnoreCase(tipo);
        var receitas = receitaRepository.findAll().stream()
            .filter(r -> r.getStatus() != StatusReceita.CANCELADO)
            .toList();
        var despesas = despesaRepository.findAll();

        Map<String, BigDecimal> receitasPorPeriodo = new LinkedHashMap<>();
        Map<String, BigDecimal> despesasPorPeriodo = new LinkedHashMap<>();

        for (var r : receitas) {
            var chave = chavePeriodo(r.getDataPrevisaoRecebimento(), anual);
            if (chave != null && emPeriodo(r.getDataPrevisaoRecebimento(), inicio, fim)) {
                receitasPorPeriodo.merge(chave, r.getValorBruto(), BigDecimal::add);
            }
        }
        for (var d : despesas) {
            var chave = chavePeriodo(d.getData(), anual);
            if (chave != null && emPeriodo(d.getData(), inicio, fim)) {
                despesasPorPeriodo.merge(chave, d.getValor(), BigDecimal::add);
            }
        }

        var periodos = periodosNoRange(inicio, fim, anual);
        var itens = new ArrayList<ComparativoRelatorioResponse.Item>();
        BigDecimal lucroAnterior = null;
        for (var periodo : periodos) {
            var receitasP = receitasPorPeriodo.getOrDefault(periodo, BigDecimal.ZERO);
            var despesasP = despesasPorPeriodo.getOrDefault(periodo, BigDecimal.ZERO);
            var lucro = receitasP.subtract(despesasP);

            BigDecimal variacao = null;
            if (lucroAnterior != null && lucroAnterior.signum() != 0) {
                variacao = lucro.subtract(lucroAnterior).multiply(BigDecimal.valueOf(100))
                    .divide(lucroAnterior.abs(), 2, RoundingMode.HALF_UP);
            }
            itens.add(new ComparativoRelatorioResponse.Item(periodo, receitasP, despesasP, lucro, variacao));
            lucroAnterior = lucro;
        }
        return new ComparativoRelatorioResponse(anual ? "ANUAL" : "MENSAL", itens);
    }

    public RelatorioFiscalResponse fiscal(LocalDate inicio, LocalDate fim) {
        var receitas = receitasNoPeriodo(inicio, fim, true);
        var despesas = despesasNoPeriodo(inicio, fim);

        var totalReceitas = receitas.stream().map(Receita::getValorBruto).reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalComissoes = receitas.stream().map(Receita::getValorComissao).reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalDespesas = despesas.stream().map(Despesa::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        var lucroLiquido = totalReceitas.subtract(totalComissoes).subtract(totalDespesas);

        var porCategoria = despesasCategoria(inicio, fim).categorias().stream()
            .map(c -> new FinanceiroDashboardResponse.DespesaCategoriaDado(c.categoria(), c.cor(), c.valor()))
            .toList();

        return new RelatorioFiscalResponse(
            inicio, fim, totalReceitas, totalComissoes, totalDespesas, lucroLiquido,
            receitas.size(), despesas.size(), porCategoria);
    }

    private List<Receita> receitasNoPeriodo(LocalDate inicio, LocalDate fim, boolean excluirCanceladas) {
        return receitaRepository.findAll().stream()
            .filter(r -> !excluirCanceladas || r.getStatus() != StatusReceita.CANCELADO)
            .filter(r -> emPeriodo(r.getDataPrevisaoRecebimento(), inicio, fim))
            .toList();
    }

    private List<Despesa> despesasNoPeriodo(LocalDate inicio, LocalDate fim) {
        return despesaRepository.findAll().stream()
            .filter(d -> emPeriodo(d.getData(), inicio, fim))
            .toList();
    }

    private boolean emPeriodo(LocalDate data, LocalDate inicio, LocalDate fim) {
        if (data == null || inicio == null || fim == null) return false;
        return !data.isBefore(inicio) && !data.isAfter(fim);
    }

    private String chavePeriodo(LocalDate data, boolean anual) {
        if (data == null) return null;
        return anual ? String.valueOf(data.getYear()) : data.getYear() + "-" + String.format("%02d", data.getMonthValue());
    }

    private List<String> periodosNoRange(LocalDate inicio, LocalDate fim, boolean anual) {
        var resultado = new ArrayList<String>();
        if (anual) {
            for (int ano = inicio.getYear(); ano <= fim.getYear(); ano++) {
                resultado.add(String.valueOf(ano));
            }
        } else {
            var ym = YearMonth.from(inicio);
            var ultimo = YearMonth.from(fim);
            while (!ym.isAfter(ultimo)) {
                resultado.add(ym.getYear() + "-" + String.format("%02d", ym.getMonthValue()));
                ym = ym.plusMonths(1);
            }
        }
        return resultado;
    }
}
