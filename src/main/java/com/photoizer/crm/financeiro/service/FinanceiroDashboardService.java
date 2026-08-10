package com.photoizer.crm.financeiro.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.despesa.model.StatusDespesa;
import com.photoizer.crm.despesa.repository.DespesaRepository;
import com.photoizer.crm.financeiro.api.FinanceiroDashboardResponse;
import com.photoizer.crm.financeiro.model.Receita;
import com.photoizer.crm.financeiro.model.StatusReceita;
import com.photoizer.crm.financeiro.model.TipoServico;
import com.photoizer.crm.financeiro.repository.ReceitaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class FinanceiroDashboardService {

    private static final Set<StatusAgendamento> STATUS_IGNORADOS = Set.of(
        StatusAgendamento.CANCELADO, StatusAgendamento.NO_SHOW);

    private final ReceitaRepository receitaRepository;
    private final DespesaRepository despesaRepository;
    private final AgendamentoRepository agendamentoRepository;

    public FinanceiroDashboardService(ReceitaRepository receitaRepository,
                                      DespesaRepository despesaRepository,
                                      AgendamentoRepository agendamentoRepository) {
        this.receitaRepository = receitaRepository;
        this.despesaRepository = despesaRepository;
        this.agendamentoRepository = agendamentoRepository;
    }

    public FinanceiroDashboardResponse calcular(LocalDate dataInicio, LocalDate dataFim,
                                                TipoServico tipoServico, StatusReceita status,
                                                UUID clienteId, String formaPagamento) {
        var receitas = carregarReceitas(tipoServico, status, clienteId, formaPagamento);
        var despesas = despesaRepository.findAll();
        var agendamentos = agendamentoRepository.findAll().stream()
            .filter(a -> !STATUS_IGNORADOS.contains(a.getStatus()))
            .toList();

        boolean temPeriodo = dataInicio != null && dataFim != null;
        var inicio = dataInicio != null ? dataInicio : LocalDate.of(1970, 1, 1);
        var fim = dataFim != null ? dataFim : LocalDate.now();

        var cards = calcularCards(inicio, fim, receitas, despesas, agendamentos, temPeriodo);
        var meses = mesesNoRange(temPeriodo ? inicio : YearMonth.now().minusMonths(5).atDay(1),
            temPeriodo ? fim : YearMonth.now().atEndOfMonth());

        var barraMensal = calcularBarraMensal(meses, inicio, fim, receitas, despesas, agendamentos, temPeriodo);
        var despesasPorCategoria = calcularDespesasPorCategoria(inicio, fim, despesas);
        var lucroMensal = calcularLucroMensal(meses, inicio, fim, receitas, despesas, agendamentos, temPeriodo);
        var rentabilidadePorServico = calcularRentabilidadePorServico(inicio, fim, receitas);
        var rentabilidadePorTrabalho = calcularRentabilidadePorTrabalho(inicio, fim, receitas, despesas);
        var ultimosLancamentos = calcularUltimosLancamentos(inicio, fim, receitas, despesas);

        return new FinanceiroDashboardResponse(cards, barraMensal, despesasPorCategoria, lucroMensal,
            rentabilidadePorServico, rentabilidadePorTrabalho, ultimosLancamentos);
    }

    private List<Receita> carregarReceitas(TipoServico tipoServico, StatusReceita status,
                                           UUID clienteId, String formaPagamento) {
        Specification<Receita> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (tipoServico != null) predicates.add(cb.equal(root.get("tipoServico"), tipoServico));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (clienteId != null) predicates.add(cb.equal(root.get("clienteId"), clienteId));
            if (formaPagamento != null && !formaPagamento.isBlank()) {
                predicates.add(cb.equal(root.get("formaPagamento"),
                    com.photoizer.crm.shared.model.FormaPagamento.valueOf(formaPagamento)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return receitaRepository.findAll(spec);
    }

    private FinanceiroDashboardResponse.CardsResumo calcularCards(
            LocalDate inicio, LocalDate fim, List<Receita> receitas, List<Despesa> despesas,
            List<Agendamento> agendamentos, boolean temPeriodo) {

        var valorBruto = BigDecimal.ZERO;
        var comissao = BigDecimal.ZERO;
        var realizadas = BigDecimal.ZERO;
        var aReceber = BigDecimal.ZERO;
        int qtdTrabalhos = 0;

        for (var r : receitas) {
            if (r.getStatus() == StatusReceita.CANCELADO) continue;

            if (emPeriodo(r.getDataPrevisaoRecebimento(), inicio, fim)) {
                valorBruto = valorBruto.add(r.getValorBruto());
                comissao = comissao.add(r.getValorComissao());
                qtdTrabalhos++;
            }
            if (r.getStatus() == StatusReceita.PAGO_TOTAL
                && (r.getDataRecebimentoReal() != null
                    && r.getDataRecebimentoReal().toLocalDate().isAfter(inicio.minusDays(1))
                    && r.getDataRecebimentoReal().toLocalDate().isBefore(fim.plusDays(1)))) {
                realizadas = realizadas.add(r.getValorRecebido());
            }
            if (r.getStatus() == StatusReceita.PENDENTE || r.getStatus() == StatusReceita.PAGO_PARCIAL) {
                aReceber = aReceber.add(r.getValorFinal().subtract(r.getValorRecebido()));
            }
        }

        var despesasPeriodo = despesas.stream()
            .filter(d -> emPeriodo(d.getData(), inicio, fim))
            .map(Despesa::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var despesasPagas = despesas.stream()
            .filter(d -> d.getStatus() == StatusDespesa.PAGO && emPeriodo(d.getData(), inicio, fim))
            .map(Despesa::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var custoDeslocamento = agendamentos.stream()
            .filter(a -> emPeriodo(a.getDataHoraEnsaio() != null ? a.getDataHoraEnsaio().toLocalDate() : null, inicio, fim))
            .map(a -> a.getCustoDeslocamento() != null ? a.getCustoDeslocamento() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var despesasTotais = despesasPeriodo.add(custoDeslocamento).add(comissao);
        var liquidoPrevisto = valorBruto.subtract(despesasTotais);
        var liquidoRealizado = realizadas.subtract(despesasPagas);
        var margemLucro = valorBruto.signum() > 0
            ? liquidoPrevisto.multiply(BigDecimal.valueOf(100)).divide(valorBruto, 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        var ticketMedio = qtdTrabalhos > 0
            ? valorBruto.divide(BigDecimal.valueOf(qtdTrabalhos), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        var variacoes = temPeriodo
            ? calcularVariacoes(inicio, fim, receitas, despesas, agendamentos)
            : null;

        return new FinanceiroDashboardResponse.CardsResumo(
            valorBruto, despesasTotais, liquidoPrevisto, liquidoRealizado, aReceber,
            margemLucro, ticketMedio, qtdTrabalhos, variacoes
        );
    }

    private FinanceiroDashboardResponse.VariacaoCards calcularVariacoes(
            LocalDate inicio, LocalDate fim, List<Receita> receitas, List<Despesa> despesas,
            List<Agendamento> agendamentos) {
        var tamanho = fim.toEpochDay() - inicio.toEpochDay() + 1;
        var prevInicio = inicio.minusDays(tamanho);
        var prevFim = inicio.minusDays(1);

        var bruto = BigDecimal.ZERO;
        var comissao = BigDecimal.ZERO;
        var realizadas = BigDecimal.ZERO;
        for (var r : receitas) {
            if (r.getStatus() == StatusReceita.CANCELADO) continue;
            if (emPeriodo(r.getDataPrevisaoRecebimento(), prevInicio, prevFim)) {
                bruto = bruto.add(r.getValorBruto());
                comissao = comissao.add(r.getValorComissao());
            }
            if (r.getStatus() == StatusReceita.PAGO_TOTAL
                && r.getDataRecebimentoReal() != null
                && emPeriodo(r.getDataRecebimentoReal().toLocalDate(), prevInicio, prevFim)) {
                realizadas = realizadas.add(r.getValorRecebido());
            }
        }
        var despesasPrev = despesas.stream()
            .filter(d -> emPeriodo(d.getData(), prevInicio, prevFim))
            .map(Despesa::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        var deslocamentoPrev = agendamentos.stream()
            .filter(a -> a.getDataHoraEnsaio() != null && emPeriodo(a.getDataHoraEnsaio().toLocalDate(), prevInicio, prevFim))
            .map(a -> a.getCustoDeslocamento() != null ? a.getCustoDeslocamento() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var despesasPagasPrev = despesas.stream()
            .filter(d -> d.getStatus() == StatusDespesa.PAGO && emPeriodo(d.getData(), prevInicio, prevFim))
            .map(Despesa::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new FinanceiroDashboardResponse.VariacaoCards(
            bruto, despesasPrev.add(deslocamentoPrev).add(comissao),
            bruto.subtract(despesasPrev).subtract(deslocamentoPrev).subtract(comissao),
            realizadas.subtract(despesasPagasPrev)
        );
    }

    private List<FinanceiroDashboardResponse.DadoMensal> calcularBarraMensal(
            List<YearMonth> meses, LocalDate inicio, LocalDate fim,
            List<Receita> receitas, List<Despesa> despesas, List<Agendamento> agendamentos, boolean temPeriodo) {
        var receitasPorMes = new HashMap<YearMonth, BigDecimal>();
        for (var r : receitas) {
            if (r.getStatus() == StatusReceita.CANCELADO) continue;
            if (!temPeriodo || emPeriodo(r.getDataPrevisaoRecebimento(), inicio, fim)) {
                if (r.getDataPrevisaoRecebimento() != null) {
                    receitasPorMes.merge(YearMonth.from(r.getDataPrevisaoRecebimento()), r.getValorBruto(), BigDecimal::add);
                }
            }
        }
        var despesasPorMes = new HashMap<YearMonth, BigDecimal>();
        for (var d : despesas) {
            if (!temPeriodo || emPeriodo(d.getData(), inicio, fim)) {
                despesasPorMes.merge(YearMonth.from(d.getData()), d.getValor(), BigDecimal::add);
            }
        }
        for (var a : agendamentos) {
            if (a.getCustoDeslocamento() == null || a.getDataHoraEnsaio() == null) continue;
            if (!temPeriodo || emPeriodo(a.getDataHoraEnsaio().toLocalDate(), inicio, fim)) {
                despesasPorMes.merge(YearMonth.from(a.getDataHoraEnsaio()), a.getCustoDeslocamento(), BigDecimal::add);
            }
        }

        var resultado = new ArrayList<FinanceiroDashboardResponse.DadoMensal>();
        for (var ym : meses) {
            resultado.add(new FinanceiroDashboardResponse.DadoMensal(
                ym.format(DateTimeFormatter.ofPattern("yyyy-MM")),
                receitasPorMes.getOrDefault(ym, BigDecimal.ZERO),
                despesasPorMes.getOrDefault(ym, BigDecimal.ZERO)
            ));
        }
        return resultado;
    }

    private List<FinanceiroDashboardResponse.DespesaCategoriaDado> calcularDespesasPorCategoria(
            LocalDate inicio, LocalDate fim, List<Despesa> despesas) {
        Map<String, BigDecimal> valores = new LinkedHashMap<>();
        Map<String, String> cores = new LinkedHashMap<>();
        for (var d : despesas) {
            if (!emPeriodo(d.getData(), inicio, fim)) continue;
            var nome = d.getCategoria() != null ? d.getCategoria() : "Outros";
            valores.merge(nome, d.getValor(), BigDecimal::add);
            var cor = d.getCategoriaRef() != null ? d.getCategoriaRef().getCor() : null;
            if (cor != null) cores.putIfAbsent(nome, cor);
        }
        return valores.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .map(e -> new FinanceiroDashboardResponse.DespesaCategoriaDado(
                e.getKey(), cores.get(e.getKey()), e.getValue()))
            .toList();
    }

    private List<FinanceiroDashboardResponse.DadoLucroMensal> calcularLucroMensal(
            List<YearMonth> meses, LocalDate inicio, LocalDate fim,
            List<Receita> receitas, List<Despesa> despesas, List<Agendamento> agendamentos, boolean temPeriodo) {
        var recebidoPorMes = new HashMap<YearMonth, BigDecimal>();
        for (var r : receitas) {
            if (r.getStatus() != StatusReceita.PAGO_TOTAL || r.getDataRecebimentoReal() == null) continue;
            if (!temPeriodo || emPeriodo(r.getDataRecebimentoReal().toLocalDate(), inicio, fim)) {
                recebidoPorMes.merge(YearMonth.from(r.getDataRecebimentoReal()), r.getValorRecebido(), BigDecimal::add);
            }
        }
        var despesasPorMes = new HashMap<YearMonth, BigDecimal>();
        for (var d : despesas) {
            if (d.getStatus() != StatusDespesa.PAGO) continue;
            if (!temPeriodo || emPeriodo(d.getData(), inicio, fim)) {
                despesasPorMes.merge(YearMonth.from(d.getData()), d.getValor(), BigDecimal::add);
            }
        }
        for (var a : agendamentos) {
            if (a.getCustoDeslocamento() == null || a.getDataHoraEnsaio() == null) continue;
            if (!temPeriodo || emPeriodo(a.getDataHoraEnsaio().toLocalDate(), inicio, fim)) {
                despesasPorMes.merge(YearMonth.from(a.getDataHoraEnsaio()), a.getCustoDeslocamento(), BigDecimal::add);
            }
        }

        var resultado = new ArrayList<FinanceiroDashboardResponse.DadoLucroMensal>();
        for (var ym : meses) {
            var liquido = recebidoPorMes.getOrDefault(ym, BigDecimal.ZERO)
                .subtract(despesasPorMes.getOrDefault(ym, BigDecimal.ZERO));
            resultado.add(new FinanceiroDashboardResponse.DadoLucroMensal(
                ym.format(DateTimeFormatter.ofPattern("yyyy-MM")), liquido));
        }
        return resultado;
    }

    private List<FinanceiroDashboardResponse.RentabilidadeServico> calcularRentabilidadePorServico(
            LocalDate inicio, LocalDate fim, List<Receita> receitas) {
        var receita = new HashMap<TipoServico, BigDecimal>();
        var liquido = new HashMap<TipoServico, BigDecimal>();
        for (var r : receitas) {
            if (r.getStatus() == StatusReceita.CANCELADO) continue;
            if (!emPeriodo(r.getDataPrevisaoRecebimento(), inicio, fim)) continue;
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

    private List<FinanceiroDashboardResponse.RentabilidadeTrabalho> calcularRentabilidadePorTrabalho(
            LocalDate inicio, LocalDate fim, List<Receita> receitas, List<Despesa> despesas) {
        var custoPorTrabalho = despesas.stream()
            .filter(d -> d.getAgendamentoId() != null)
            .collect(Collectors.groupingBy(Despesa::getAgendamentoId,
                Collectors.reducing(BigDecimal.ZERO, Despesa::getValor, BigDecimal::add)));

        Map<UUID, List<Receita>> porTrabalho = new HashMap<>();
        for (var r : receitas) {
            if (r.getAgendamentoId() == null || r.getStatus() == StatusReceita.CANCELADO) continue;
            if (!emPeriodo(r.getDataPrevisaoRecebimento(), inicio, fim)) continue;
            porTrabalho.computeIfAbsent(r.getAgendamentoId(), k -> new ArrayList<>()).add(r);
        }

        var resultado = new ArrayList<FinanceiroDashboardResponse.RentabilidadeTrabalho>();
        for (var entry : porTrabalho.entrySet()) {
            var lista = entry.getValue();
            var valor = lista.stream().map(Receita::getValorBruto).reduce(BigDecimal.ZERO, BigDecimal::add);
            var custo = custoPorTrabalho.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            var primeiro = lista.get(0);
            var roi = custo.signum() > 0
                ? valor.subtract(custo).multiply(BigDecimal.valueOf(100)).divide(custo, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            var margem = valor.signum() > 0
                ? valor.subtract(custo).multiply(BigDecimal.valueOf(100)).divide(valor, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            resultado.add(new FinanceiroDashboardResponse.RentabilidadeTrabalho(
                entry.getKey(), primeiro.getClienteNome(), primeiro.getTipoServico().name(),
                valor, custo, roi, margem));
        }
        resultado.sort((a, b) -> b.roi().compareTo(a.roi()));
        return resultado;
    }

    private List<FinanceiroDashboardResponse.Lancamento> calcularUltimosLancamentos(
            LocalDate inicio, LocalDate fim, List<Receita> receitas, List<Despesa> despesas) {
        var lancamentos = new ArrayList<FinanceiroDashboardResponse.Lancamento>();

        for (var r : receitas) {
            if (r.getStatus() == StatusReceita.CANCELADO) continue;
            var data = r.getDataPrevisaoRecebimento() != null
                ? r.getDataPrevisaoRecebimento()
                : (r.getDataRecebimentoReal() != null ? r.getDataRecebimentoReal().toLocalDate() : null);
            if (data == null || !emPeriodo(data, inicio, fim)) continue;
            var descricao = r.getDescricao() != null && !r.getDescricao().isBlank()
                ? r.getDescricao()
                : r.getClienteNome() + " — " + labelServico(r.getTipoServico());
            lancamentos.add(new FinanceiroDashboardResponse.Lancamento(
                r.getId().toString(), "RECEITA", data, descricao,
                labelServico(r.getTipoServico()), r.getValorFinal(),
                r.getStatus().name(),
                r.getAgendamentoId() != null ? "AGENDAMENTO" : "MANUAL"
            ));
        }

        for (var d : despesas) {
            if (!emPeriodo(d.getData(), inicio, fim)) continue;
            lancamentos.add(new FinanceiroDashboardResponse.Lancamento(
                d.getId().toString(), "DESPESA", d.getData(), d.getDescricao(),
                d.getCategoria() != null ? d.getCategoria() : "Outros", d.getValor(),
                d.getStatus().name(),
                d.getGeradaDeId() != null ? "RECORRENTE" : "MANUAL"
            ));
        }

        return lancamentos.stream()
            .sorted((a, b) -> b.data().compareTo(a.data()))
            .limit(10)
            .toList();
    }

    private List<YearMonth> mesesNoRange(LocalDate inicio, LocalDate fim) {
        var resultado = new ArrayList<YearMonth>();
        var ym = YearMonth.from(inicio);
        var ultimo = YearMonth.from(fim);
        while (!ym.isAfter(ultimo)) {
            resultado.add(ym);
            ym = ym.plusMonths(1);
        }
        return resultado;
    }

    private boolean emPeriodo(LocalDate data, LocalDate inicio, LocalDate fim) {
        if (data == null) return false;
        return !data.isBefore(inicio) && !data.isAfter(fim);
    }

    private String labelServico(TipoServico tipoServico) {
        return switch (tipoServico) {
            case ENSAIO -> "Ensaio";
            case CASAMENTO -> "Casamento";
            case EVENTO -> "Evento";
            case PRODUTO -> "Produto";
            case OUTRO -> "Outro";
        };
    }
}
