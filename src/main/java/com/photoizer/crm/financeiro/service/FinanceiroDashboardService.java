package com.photoizer.crm.financeiro.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.comissao.model.Indicacao;
import com.photoizer.crm.comissao.repository.IndicacaoRepository;
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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class FinanceiroDashboardService {

    private static final Set<StatusAgendamento> STATUS_IGNORADOS = Set.of(
        StatusAgendamento.CANCELADO, StatusAgendamento.NO_SHOW);

    private static final LocalDate MAX_FIM = LocalDate.of(2100, 12, 31);

    private static final String STATUS_COMISSAO_CANCELADA = "CANCELADA";
    private static final String STATUS_COMISSAO_PAGA = "PAGA";

    private final ReceitaRepository receitaRepository;
    private final DespesaRepository despesaRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final IndicacaoRepository indicacaoRepository;

    public FinanceiroDashboardService(ReceitaRepository receitaRepository,
                                      DespesaRepository despesaRepository,
                                      AgendamentoRepository agendamentoRepository,
                                      IndicacaoRepository indicacaoRepository) {
        this.receitaRepository = receitaRepository;
        this.despesaRepository = despesaRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.indicacaoRepository = indicacaoRepository;
    }

    public FinanceiroDashboardResponse calcular(LocalDate dataInicio, LocalDate dataFim,
                                                TipoServico tipoServico, StatusReceita status,
                                                UUID clienteId, String formaPagamento) {
        var receitas = carregarReceitasAvulsas(tipoServico, status, clienteId, formaPagamento);
        var despesas = despesaRepository.findAll();
        var agendamentos = agendamentoRepository.findAll().stream()
            .filter(a -> !STATUS_IGNORADOS.contains(a.getStatus()))
            .filter(a -> clienteId == null || a.getCliente().getId().equals(clienteId))
            .toList();
        var indicacoes = indicacaoRepository.findAll();

        boolean temPeriodo = dataInicio != null && dataFim != null;
        var inicio = dataInicio != null ? dataInicio : LocalDate.of(1970, 1, 1);
        var fim = dataFim != null ? dataFim : MAX_FIM;

        var cards = calcularCards(inicio, fim, receitas, despesas, agendamentos, indicacoes, temPeriodo);
        var meses = mesesNoRange(temPeriodo ? inicio : YearMonth.now().minusMonths(5).atDay(1),
            temPeriodo ? fim : YearMonth.now().atEndOfMonth());

        var barraMensal = calcularBarraMensal(meses, inicio, fim, receitas, despesas, agendamentos, indicacoes, temPeriodo);
        var despesasPorCategoria = calcularDespesasPorCategoria(inicio, fim, despesas);
        var lucroMensal = calcularLucroMensal(meses, inicio, fim, receitas, despesas, agendamentos, indicacoes, temPeriodo);
        var rentabilidadePorServico = calcularRentabilidadePorServico(inicio, fim, receitas, despesas, agendamentos, indicacoes);
        var rentabilidadePorTrabalho = calcularRentabilidadePorTrabalho(inicio, fim, despesas, agendamentos, indicacoes);
        var ultimosLancamentos = calcularUltimosLancamentos(inicio, fim, receitas, despesas, agendamentos);

        return new FinanceiroDashboardResponse(cards, barraMensal, despesasPorCategoria, lucroMensal,
            rentabilidadePorServico, rentabilidadePorTrabalho, ultimosLancamentos);
    }

    private List<Receita> carregarReceitasAvulsas(TipoServico tipoServico, StatusReceita status,
                                                  UUID clienteId, String formaPagamento) {
        Specification<Receita> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(root.get("agendamentoId").isNull());
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
            List<Agendamento> agendamentos, List<Indicacao> indicacoes,
            boolean temPeriodo) {

        var agg = calcularAgregados(inicio, fim, receitas, despesas, agendamentos, indicacoes);

        var margemLucro = agg.valorBruto().signum() > 0
            ? agg.liquidoPrevisto().multiply(BigDecimal.valueOf(100)).divide(agg.valorBruto(), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        var ticketMedio = agg.qtdTrabalhos() > 0
            ? agg.valorBruto().divide(BigDecimal.valueOf(agg.qtdTrabalhos()), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        var variacoes = temPeriodo
            ? calcularVariacoes(inicio, fim, receitas, despesas, agendamentos, indicacoes)
            : null;

        var detalhamento = new FinanceiroDashboardResponse.Detalhamento(
            agg.realizadas(),
            agg.entradaEnsaios(),
            agg.restanteEnsaios(),
            BigDecimal.ZERO,
            agg.receitasAvulsasBruto(),
            agg.comissao(),
            agg.deslocamentoEfetivo(),
            agg.despesasPeriodo()
        );

        return new FinanceiroDashboardResponse.CardsResumo(
            agg.valorBruto(), agg.despesasTotais(), agg.liquidoPrevisto(), agg.liquidoRealizado(),
            agg.aReceber(), margemLucro, ticketMedio, agg.qtdTrabalhos(), variacoes, detalhamento
        );
    }

    private Agregados calcularAgregados(
            LocalDate inicio, LocalDate fim, List<Receita> receitas, List<Despesa> despesas,
            List<Agendamento> agendamentos, List<Indicacao> indicacoes) {

        var ensaiosPeriodo = agendamentos.stream()
            .filter(a -> emPeriodo(a.getDataHoraEnsaio() != null ? a.getDataHoraEnsaio().toLocalDate() : null, inicio, fim))
            .toList();

        var entradaEnsaios = somar(ensaiosPeriodo, Agendamento::getValorEntradaPago);
        var restanteEnsaios = somar(ensaiosPeriodo, Agendamento::getValorRestante);
        var deslocamentoEfetivo = somar(ensaiosPeriodo, this::deslocamentoEfetivo);
        var deslocamentoEfetivoPago = somar(ensaiosPeriodo.stream()
            .filter(a -> a.getValorRestante() != null && a.getValorRestante().compareTo(BigDecimal.ZERO) <= 0)
            .toList(), this::deslocamentoEfetivo);
        var qtdTrabalhos = ensaiosPeriodo.size();

        var idsEnsaios = ensaiosPeriodo.stream().map(Agendamento::getId).collect(Collectors.toSet());
        var comissao = somar(indicacoes.stream()
            .filter(i -> idsEnsaios.contains(i.getAgendamentoId()))
            .filter(i -> !STATUS_COMISSAO_CANCELADA.equals(i.getStatus()))
            .toList(), Indicacao::getValorComissao);
        var comissaoPaga = somar(indicacoes.stream()
            .filter(i -> idsEnsaios.contains(i.getAgendamentoId()))
            .filter(i -> STATUS_COMISSAO_PAGA.equals(i.getStatus()))
            .toList(), Indicacao::getValorComissao);

        var avulsasBruto = BigDecimal.ZERO;
        var avulsasRealizadas = BigDecimal.ZERO;
        var aReceberAvulsas = BigDecimal.ZERO;
        for (var r : receitas) {
            if (r.getStatus() == StatusReceita.CANCELADO) continue;
            if (emPeriodo(r.getDataPrevisaoRecebimento(), inicio, fim)) {
                avulsasBruto = avulsasBruto.add(r.getValorBruto());
            }
            if (r.getDataRecebimentoReal() != null
                && emPeriodo(r.getDataRecebimentoReal().toLocalDate(), inicio, fim)) {
                avulsasRealizadas = avulsasRealizadas.add(r.getValorRecebido());
            }
            if ((r.getStatus() == StatusReceita.PENDENTE || r.getStatus() == StatusReceita.PAGO_PARCIAL)
                && emPeriodo(r.getDataPrevisaoRecebimento(), inicio, fim)) {
                aReceberAvulsas = aReceberAvulsas.add(r.getValorFinal().subtract(r.getValorRecebido()));
            }
        }

        var despesasPeriodo = somar(despesas.stream()
            .filter(d -> emPeriodo(d.getData(), inicio, fim)).toList(), Despesa::getValor);
        var despesasPagas = somar(despesas.stream()
            .filter(d -> d.getStatus() == StatusDespesa.PAGO && emPeriodo(d.getData(), inicio, fim)).toList(),
            Despesa::getValor);

        var valorBruto = entradaEnsaios.add(restanteEnsaios).add(avulsasBruto);
        var realizadas = entradaEnsaios.add(avulsasRealizadas);
        var aReceber = restanteEnsaios.add(aReceberAvulsas);

        return new Agregados(
            entradaEnsaios, restanteEnsaios, avulsasBruto, avulsasRealizadas, aReceberAvulsas,
            comissao, comissaoPaga, deslocamentoEfetivo, deslocamentoEfetivoPago,
            despesasPeriodo, despesasPagas, qtdTrabalhos, valorBruto, realizadas, aReceber
        );
    }

    private FinanceiroDashboardResponse.VariacaoCards calcularVariacoes(
            LocalDate inicio, LocalDate fim, List<Receita> receitas, List<Despesa> despesas,
            List<Agendamento> agendamentos, List<Indicacao> indicacoes) {
        var tamanho = fim.toEpochDay() - inicio.toEpochDay() + 1;
        var prevInicio = inicio.minusDays(tamanho);
        var prevFim = inicio.minusDays(1);

        var agg = calcularAgregados(prevInicio, prevFim, receitas, despesas, agendamentos, indicacoes);

        return new FinanceiroDashboardResponse.VariacaoCards(
            agg.valorBruto(), agg.despesasTotais(), agg.liquidoPrevisto(), agg.liquidoRealizado()
        );
    }

    private List<FinanceiroDashboardResponse.DadoMensal> calcularBarraMensal(
            List<YearMonth> meses, LocalDate inicio, LocalDate fim,
            List<Receita> receitas, List<Despesa> despesas, List<Agendamento> agendamentos,
            List<Indicacao> indicacoes, boolean temPeriodo) {
        var receitasPorMes = new HashMap<YearMonth, BigDecimal>();
        var despesasPorMes = new HashMap<YearMonth, BigDecimal>();
        var dataEnsaioPorId = dataEnsaioPorId(agendamentos);

        for (var a : agendamentos) {
            var data = a.getDataHoraEnsaio() != null ? a.getDataHoraEnsaio().toLocalDate() : null;
            if (temPeriodo && !emPeriodo(data, inicio, fim)) continue;
            if (data == null) continue;
            receitasPorMes.merge(YearMonth.from(data), a.getValorTotalFinal(), BigDecimal::add);
            despesasPorMes.merge(YearMonth.from(data), deslocamentoEfetivo(a), BigDecimal::add);
        }
        for (var i : indicacoes) {
            if (STATUS_COMISSAO_CANCELADA.equals(i.getStatus())) continue;
            var data = dataEnsaioPorId.get(i.getAgendamentoId());
            if (data == null || (temPeriodo && !emPeriodo(data, inicio, fim))) continue;
            despesasPorMes.merge(YearMonth.from(data), i.getValorComissao(), BigDecimal::add);
        }
        for (var r : receitas) {
            if (r.getStatus() == StatusReceita.CANCELADO) continue;
            var data = r.getDataPrevisaoRecebimento();
            if (data == null || (temPeriodo && !emPeriodo(data, inicio, fim))) continue;
            receitasPorMes.merge(YearMonth.from(data), r.getValorBruto(), BigDecimal::add);
        }
        for (var d : despesas) {
            if (temPeriodo && !emPeriodo(d.getData(), inicio, fim)) continue;
            despesasPorMes.merge(YearMonth.from(d.getData()), d.getValor(), BigDecimal::add);
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
            List<Receita> receitas, List<Despesa> despesas, List<Agendamento> agendamentos,
            List<Indicacao> indicacoes, boolean temPeriodo) {
        var recebidoPorMes = new HashMap<YearMonth, BigDecimal>();
        var despesasPorMes = new HashMap<YearMonth, BigDecimal>();
        var dataEnsaioPorId = dataEnsaioPorId(agendamentos);

        for (var a : agendamentos) {
            var data = a.getDataHoraEnsaio() != null ? a.getDataHoraEnsaio().toLocalDate() : null;
            if (temPeriodo && !emPeriodo(data, inicio, fim)) continue;
            if (data == null) continue;
            recebidoPorMes.merge(YearMonth.from(data), a.getValorEntradaPago(), BigDecimal::add);
            despesasPorMes.merge(YearMonth.from(data), deslocamentoEfetivo(a), BigDecimal::add);
        }
        for (var i : indicacoes) {
            if (!STATUS_COMISSAO_PAGA.equals(i.getStatus())) continue;
            var data = dataEnsaioPorId.get(i.getAgendamentoId());
            if (data == null || (temPeriodo && !emPeriodo(data, inicio, fim))) continue;
            despesasPorMes.merge(YearMonth.from(data), i.getValorComissao(), BigDecimal::add);
        }
        for (var r : receitas) {
            if (r.getDataRecebimentoReal() == null) continue;
            var data = r.getDataRecebimentoReal().toLocalDate();
            if (temPeriodo && !emPeriodo(data, inicio, fim)) continue;
            recebidoPorMes.merge(YearMonth.from(data), r.getValorRecebido(), BigDecimal::add);
        }
        for (var d : despesas) {
            if (d.getStatus() != StatusDespesa.PAGO) continue;
            if (temPeriodo && !emPeriodo(d.getData(), inicio, fim)) continue;
            despesasPorMes.merge(YearMonth.from(d.getData()), d.getValor(), BigDecimal::add);
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
            LocalDate inicio, LocalDate fim, List<Receita> receitas, List<Despesa> despesas,
            List<Agendamento> agendamentos, List<Indicacao> indicacoes) {
        var custoPorTrabalho = custoDespesasPorTrabalho(despesas);
        var comissaoPorTrabalho = comissaoPorTrabalho(indicacoes);

        var receita = new HashMap<String, BigDecimal>();
        var liquido = new HashMap<String, BigDecimal>();
        for (var a : agendamentos) {
            var data = a.getDataHoraEnsaio() != null ? a.getDataHoraEnsaio().toLocalDate() : null;
            if (!emPeriodo(data, inicio, fim)) continue;
            var valor = a.getValorTotalFinal();
            var custo = custoPorTrabalho.getOrDefault(a.getId(), BigDecimal.ZERO)
                .add(deslocamentoEfetivo(a))
                .add(comissaoPorTrabalho.getOrDefault(a.getId(), BigDecimal.ZERO));
            receita.merge("ENSAIO", valor, BigDecimal::add);
            liquido.merge("ENSAIO", valor.subtract(custo), BigDecimal::add);
        }
        for (var r : receitas) {
            if (r.getStatus() == StatusReceita.CANCELADO) continue;
            if (!emPeriodo(r.getDataPrevisaoRecebimento(), inicio, fim)) continue;
            receita.merge(r.getTipoServico().name(), r.getValorBruto(), BigDecimal::add);
            liquido.merge(r.getTipoServico().name(), r.getValorFinal(), BigDecimal::add);
        }
        return receita.entrySet().stream()
            .map(e -> {
                var l = liquido.getOrDefault(e.getKey(), BigDecimal.ZERO);
                var margem = e.getValue().signum() > 0
                    ? l.multiply(BigDecimal.valueOf(100)).divide(e.getValue(), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
                return new FinanceiroDashboardResponse.RentabilidadeServico(
                    e.getKey(), e.getValue(), l, margem);
            })
            .sorted((a, b) -> b.receita().compareTo(a.receita()))
            .toList();
    }

    private List<FinanceiroDashboardResponse.RentabilidadeTrabalho> calcularRentabilidadePorTrabalho(
            LocalDate inicio, LocalDate fim, List<Despesa> despesas,
            List<Agendamento> agendamentos, List<Indicacao> indicacoes) {
        var custoPorTrabalho = custoDespesasPorTrabalho(despesas);
        var comissaoPorTrabalho = comissaoPorTrabalho(indicacoes);

        var resultado = new ArrayList<FinanceiroDashboardResponse.RentabilidadeTrabalho>();
        for (var a : agendamentos) {
            var data = a.getDataHoraEnsaio() != null ? a.getDataHoraEnsaio().toLocalDate() : null;
            if (!emPeriodo(data, inicio, fim)) continue;
            var valor = a.getValorTotalFinal();
            var custo = custoPorTrabalho.getOrDefault(a.getId(), BigDecimal.ZERO)
                .add(deslocamentoEfetivo(a))
                .add(comissaoPorTrabalho.getOrDefault(a.getId(), BigDecimal.ZERO));
            var roi = custo.signum() > 0
                ? valor.subtract(custo).multiply(BigDecimal.valueOf(100)).divide(custo, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            var margem = valor.signum() > 0
                ? valor.subtract(custo).multiply(BigDecimal.valueOf(100)).divide(valor, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            resultado.add(new FinanceiroDashboardResponse.RentabilidadeTrabalho(
                a.getId(), a.getCliente().getNome(), "ENSAIO",
                valor, custo, roi, margem));
        }
        resultado.sort((a, b) -> b.roi().compareTo(a.roi()));
        return resultado;
    }

    private List<FinanceiroDashboardResponse.Lancamento> calcularUltimosLancamentos(
            LocalDate inicio, LocalDate fim, List<Receita> receitas,
            List<Despesa> despesas, List<Agendamento> agendamentos) {
        var lancamentos = new ArrayList<FinanceiroDashboardResponse.Lancamento>();

        for (var a : agendamentos) {
            var data = a.getDataHoraEnsaio() != null ? a.getDataHoraEnsaio().toLocalDate() : null;
            if (!emPeriodo(data, inicio, fim)) continue;
            var pacoteNome = a.getPacote() != null ? a.getPacote().getNome() : null;
            var descricao = a.getCliente().getNome() + (pacoteNome != null ? " — " + pacoteNome : "");
            var valorEntrada = a.getValorEntradaPago() != null ? a.getValorEntradaPago() : BigDecimal.ZERO;
            var valorRestante = a.getValorRestante() != null ? a.getValorRestante() : BigDecimal.ZERO;
            var status = valorRestante.compareTo(BigDecimal.ZERO) <= 0
                ? "PAGO_TOTAL"
                : (valorEntrada.signum() > 0 ? "PAGO_PARCIAL" : "PENDENTE");
            lancamentos.add(new FinanceiroDashboardResponse.Lancamento(
                a.getId().toString(), "RECEITA", data, descricao,
                "Ensaio", a.getValorTotalFinal(), status, "AGENDAMENTO"
            ));
        }

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
                r.getStatus().name(), "MANUAL"
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

    private Map<UUID, LocalDate> dataEnsaioPorId(List<Agendamento> agendamentos) {
        Map<UUID, LocalDate> mapa = new HashMap<>();
        for (var a : agendamentos) {
            if (a.getDataHoraEnsaio() != null) {
                mapa.put(a.getId(), a.getDataHoraEnsaio().toLocalDate());
            }
        }
        return mapa;
    }

    private Map<UUID, BigDecimal> custoDespesasPorTrabalho(List<Despesa> despesas) {
        return despesas.stream()
            .filter(d -> d.getAgendamentoId() != null)
            .collect(Collectors.groupingBy(Despesa::getAgendamentoId,
                Collectors.reducing(BigDecimal.ZERO, Despesa::getValor, BigDecimal::add)));
    }

    private Map<UUID, BigDecimal> comissaoPorTrabalho(List<Indicacao> indicacoes) {
        Map<UUID, BigDecimal> mapa = new HashMap<>();
        for (var i : indicacoes) {
            if (STATUS_COMISSAO_CANCELADA.equals(i.getStatus())) continue;
            mapa.merge(i.getAgendamentoId(), i.getValorComissao(), BigDecimal::add);
        }
        return mapa;
    }

    private BigDecimal deslocamentoEfetivo(Agendamento a) {
        if (Boolean.TRUE.equals(a.getRepassarDeslocamento())) return BigDecimal.ZERO;
        return a.getCustoDeslocamento() != null ? a.getCustoDeslocamento() : BigDecimal.ZERO;
    }

    private <T> BigDecimal somar(List<T> itens, Function<T, BigDecimal> mapper) {
        var total = BigDecimal.ZERO;
        for (var item : itens) {
            var valor = mapper.apply(item);
            if (valor != null) total = total.add(valor);
        }
        return total;
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

    private record Agregados(
        BigDecimal entradaEnsaios,
        BigDecimal restanteEnsaios,
        BigDecimal receitasAvulsasBruto,
        BigDecimal receitasAvulsasRealizadas,
        BigDecimal aReceberAvulsas,
        BigDecimal comissao,
        BigDecimal comissaoPaga,
        BigDecimal deslocamentoEfetivo,
        BigDecimal deslocamentoEfetivoPago,
        BigDecimal despesasPeriodo,
        BigDecimal despesasPagas,
        int qtdTrabalhos,
        BigDecimal valorBruto,
        BigDecimal realizadas,
        BigDecimal aReceber
    ) {
        BigDecimal despesasTotais() {
            return deslocamentoEfetivo().add(comissao()).add(despesasPeriodo());
        }

        BigDecimal liquidoPrevisto() {
            return valorBruto().subtract(despesasTotais());
        }

        BigDecimal liquidoRealizado() {
            return realizadas()
                .subtract(despesasPagas().add(comissaoPaga()).add(deslocamentoEfetivoPago()));
        }
    }
}
