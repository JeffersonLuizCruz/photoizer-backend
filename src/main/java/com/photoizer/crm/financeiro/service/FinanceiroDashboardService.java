package com.photoizer.crm.financeiro.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
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
import com.photoizer.crm.shared.service.FinanceCalculator;
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
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class FinanceiroDashboardService {

    private static final LocalDate MAX_FIM = LocalDate.of(2100, 12, 31);

    private static final com.photoizer.crm.comissao.model.StatusIndicacao STATUS_COMISSAO_CANCELADA = com.photoizer.crm.comissao.model.StatusIndicacao.CANCELADA;
    private static final com.photoizer.crm.comissao.model.StatusIndicacao STATUS_COMISSAO_PAGA = com.photoizer.crm.comissao.model.StatusIndicacao.PAGA;

    private final ReceitaRepository receitaRepository;
    private final DespesaRepository despesaRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final AgendamentoFotografoRepository agendamentoFotografoRepository;
    private final IndicacaoRepository indicacaoRepository;
    private final FinanceCalculator financeCalculator;

    public FinanceiroDashboardService(ReceitaRepository receitaRepository,
                                      DespesaRepository despesaRepository,
                                      AgendamentoRepository agendamentoRepository,
                                      AgendamentoFotografoRepository agendamentoFotografoRepository,
                                      IndicacaoRepository indicacaoRepository,
                                      FinanceCalculator financeCalculator) {
        this.receitaRepository = receitaRepository;
        this.despesaRepository = despesaRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.agendamentoFotografoRepository = agendamentoFotografoRepository;
        this.indicacaoRepository = indicacaoRepository;
        this.financeCalculator = financeCalculator;
    }

    public FinanceiroDashboardResponse calcular(LocalDate dataInicio, LocalDate dataFim,
                                                TipoServico tipoServico, StatusReceita status,
                                                UUID clienteId, String formaPagamento) {
        var receitas = carregarReceitasAvulsas(tipoServico, status, clienteId, formaPagamento);

        boolean temPeriodo = dataInicio != null && dataFim != null;

        List<Despesa> despesas;
        List<Agendamento> agendamentos;
        if (temPeriodo) {
            despesas = despesaRepository.findByDataBetweenOrderByDataDesc(dataInicio, dataFim);
            agendamentos = agendamentoRepository.findByDataBetween(
                dataInicio.atStartOfDay(), dataFim.plusDays(1).atStartOfDay(),
                List.copyOf(financeCalculator.statusIgnorados()));
        } else {
            despesas = despesaRepository.findAll();
            agendamentos = agendamentoRepository.findAll().stream()
                .filter(a -> !financeCalculator.statusIgnorados().contains(a.getStatus()))
                .toList();
        }
        if (clienteId != null) {
            agendamentos = agendamentos.stream()
                .filter(a -> a.getCliente().getId().equals(clienteId))
                .toList();
        }

        var agendamentoIds = agendamentos.stream().map(Agendamento::getId).toList();
        List<Indicacao> indicacoes = agendamentoIds.isEmpty() ? List.of()
            : indicacaoRepository.findByAgendamentoIdIn(agendamentoIds);
        var repasses = financeCalculator.carregarRepasses(agendamentoFotografoRepository);

        var inicio = dataInicio != null ? dataInicio : LocalDate.of(1970, 1, 1);
        var fim = dataFim != null ? dataFim : MAX_FIM;

        var cards = calcularCards(inicio, fim, receitas, despesas, agendamentos, indicacoes, repasses, temPeriodo);
        var meses = mesesNoRange(temPeriodo ? inicio : YearMonth.now().minusMonths(5).atDay(1),
            temPeriodo ? fim : YearMonth.now().atEndOfMonth());

        var barraMensal = calcularBarraMensal(meses, inicio, fim, receitas, despesas, agendamentos, indicacoes, repasses, temPeriodo);
        var despesasPorCategoria = calcularDespesasPorCategoria(inicio, fim, despesas);
        var lucroMensal = calcularLucroMensal(meses, inicio, fim, receitas, despesas, agendamentos, indicacoes, repasses, temPeriodo);
        var rentabilidadePorServico = calcularRentabilidadePorServico(inicio, fim, receitas, despesas, agendamentos, indicacoes, repasses);
        var rentabilidadePorTrabalho = calcularRentabilidadePorTrabalho(inicio, fim, despesas, agendamentos, indicacoes, repasses);
        var ultimosLancamentos = calcularUltimosLancamentos(inicio, fim, receitas, despesas, agendamentos, repasses);

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
                try {
                    predicates.add(cb.equal(root.get("formaPagamento"),
                        com.photoizer.crm.shared.model.FormaPagamento.valueOf(formaPagamento)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return receitaRepository.findAll(spec);
    }

    private FinanceiroDashboardResponse.CardsResumo calcularCards(
            LocalDate inicio, LocalDate fim, List<Receita> receitas, List<Despesa> despesas,
            List<Agendamento> agendamentos, List<Indicacao> indicacoes,
            FinanceCalculator.RepassesResumo repasses,
            boolean temPeriodo) {

        var agg = calcularAgregados(inicio, fim, receitas, despesas, agendamentos, indicacoes, repasses);

        var margemLucro = agg.valorBruto().signum() > 0
            ? agg.liquidoPrevisto().multiply(BigDecimal.valueOf(100)).divide(agg.valorBruto(), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        var ticketMedio = agg.qtdTrabalhos() > 0
            ? agg.valorBruto().divide(BigDecimal.valueOf(agg.qtdTrabalhos()), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        var variacoes = temPeriodo
            ? calcularVariacoes(inicio, fim, receitas, despesas, agendamentos, indicacoes, repasses)
            : null;

        var detalhamento = new FinanceiroDashboardResponse.Detalhamento(
            agg.realizadas(),
            agg.entradaEnsaios(),
            agg.restanteEnsaios(),
            BigDecimal.ZERO,
            agg.receitasAvulsasBruto(),
            agg.comissao(),
            agg.deslocamentoEfetivo(),
            agg.repasses(),
            agg.despesasPeriodo()
        );

        return new FinanceiroDashboardResponse.CardsResumo(
            agg.valorBruto(), agg.despesasTotais(), agg.liquidoPrevisto(), agg.liquidoRealizado(),
            agg.aReceber(), margemLucro, ticketMedio, agg.qtdTrabalhos(), variacoes, detalhamento
        );
    }

    private Agregados calcularAgregados(
            LocalDate inicio, LocalDate fim, List<Receita> receitas, List<Despesa> despesas,
            List<Agendamento> agendamentos, List<Indicacao> indicacoes,
            FinanceCalculator.RepassesResumo repasses) {

        var ensaiosPeriodo = agendamentos.stream()
            .filter(a -> emPeriodo(a.getDataHoraEnsaio() != null ? a.getDataHoraEnsaio().toLocalDate() : null, inicio, fim))
            .toList();

        var entradaEnsaios = somar(ensaiosPeriodo, Agendamento::getValorEntradaPago);
        var restanteEnsaios = somar(ensaiosPeriodo, Agendamento::getValorRestante);
        var deslocamentoEfetivo = somar(ensaiosPeriodo, financeCalculator::deslocamentoEfetivo);
        var deslocamentoEfetivoPago = somar(ensaiosPeriodo.stream()
            .filter(a -> a.getValorRestante() != null && a.getValorRestante().compareTo(BigDecimal.ZERO) <= 0)
            .toList(), financeCalculator::deslocamentoEfetivo);
        var repassesPrevistos = somarRepassesEnsaios(ensaiosPeriodo, repasses.previstos());
        var repassesPagos = somarRepassesEnsaios(ensaiosPeriodo, repasses.pagos());
        var qtdTrabalhos = ensaiosPeriodo.size();

        var idsEnsaios = ensaiosPeriodo.stream().map(Agendamento::getId).collect(Collectors.toSet());
        var comissao = somar(indicacoes.stream()
            .filter(i -> idsEnsaios.contains(i.getAgendamentoId()))
            .filter(i -> i.getStatus() != STATUS_COMISSAO_CANCELADA)
            .toList(), Indicacao::getValorComissao);
        var comissaoPaga = somar(indicacoes.stream()
            .filter(i -> idsEnsaios.contains(i.getAgendamentoId()))
            .filter(i -> i.getStatus() == STATUS_COMISSAO_PAGA)
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
            repassesPrevistos, repassesPagos,
            despesasPeriodo, despesasPagas, qtdTrabalhos, valorBruto, realizadas, aReceber
        );
    }

    private FinanceiroDashboardResponse.VariacaoCards calcularVariacoes(
            LocalDate inicio, LocalDate fim, List<Receita> receitas, List<Despesa> despesas,
            List<Agendamento> agendamentos, List<Indicacao> indicacoes,
            FinanceCalculator.RepassesResumo repasses) {
        var prevYM = YearMonth.from(inicio).minusMonths(1);
        var prevInicio = prevYM.atDay(1);
        var prevFim = prevYM.atEndOfMonth();

        var prevDespesas = despesaRepository.findByDataBetweenOrderByDataDesc(prevInicio, prevFim);
        var prevAgendamentos = agendamentoRepository.findByDataBetween(
            prevInicio.atStartOfDay(), prevFim.plusDays(1).atStartOfDay(),
            List.copyOf(financeCalculator.statusIgnorados()));
        var prevAgendamentoIds = prevAgendamentos.stream().map(Agendamento::getId).toList();
        var prevIndicacoes = prevAgendamentoIds.isEmpty()
            ? List.<Indicacao>of()
            : indicacaoRepository.findByAgendamentoIdIn(prevAgendamentoIds);

        var agg = calcularAgregados(prevInicio, prevFim, receitas, prevDespesas, prevAgendamentos, prevIndicacoes, repasses);

        return new FinanceiroDashboardResponse.VariacaoCards(
            agg.valorBruto(), agg.despesasTotais(), agg.liquidoPrevisto(), agg.liquidoRealizado()
        );
    }

    private List<FinanceiroDashboardResponse.DadoMensal> calcularBarraMensal(
            List<YearMonth> meses, LocalDate inicio, LocalDate fim,
            List<Receita> receitas, List<Despesa> despesas, List<Agendamento> agendamentos,
            List<Indicacao> indicacoes, FinanceCalculator.RepassesResumo repasses, boolean temPeriodo) {
        var receitasPorMes = new HashMap<YearMonth, BigDecimal>();
        var despesasPorMes = new HashMap<YearMonth, BigDecimal>();
        var dataEnsaioPorId = dataEnsaioPorId(agendamentos);

        for (var a : agendamentos) {
            var data = a.getDataHoraEnsaio() != null ? a.getDataHoraEnsaio().toLocalDate() : null;
            if (temPeriodo && !emPeriodo(data, inicio, fim)) continue;
            if (data == null) continue;
            var ym = YearMonth.from(data);
            receitasPorMes.merge(ym, a.getValorTotalFinal(), BigDecimal::add);
            despesasPorMes.merge(ym, financeCalculator.deslocamentoEfetivo(a), BigDecimal::add);
            despesasPorMes.merge(ym, repassePrevisto(a.getId(), repasses), BigDecimal::add);
        }
        for (var i : indicacoes) {
            if (i.getStatus() == STATUS_COMISSAO_CANCELADA) continue;
            var data = dataEnsaioPorId.get(i.getAgendamentoId());
            if (data == null || (temPeriodo && !emPeriodo(data, inicio, fim))) continue;
            despesasPorMes.merge(YearMonth.from(data), i.getValorComissao(), BigDecimal::add);
        }
        for (var r : receitas) {
            if (r.getStatus() == StatusReceita.CANCELADO) continue;
            var data = r.getDataPrevisaoRecebimento() != null
                ? r.getDataPrevisaoRecebimento()
                : (r.getDataRecebimentoReal() != null ? r.getDataRecebimentoReal().toLocalDate() : null);
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
            List<Indicacao> indicacoes, FinanceCalculator.RepassesResumo repasses, boolean temPeriodo) {
        var recebidoPorMes = new HashMap<YearMonth, BigDecimal>();
        var despesasPorMes = new HashMap<YearMonth, BigDecimal>();
        var dataEnsaioPorId = dataEnsaioPorId(agendamentos);

        for (var a : agendamentos) {
            var data = a.getDataHoraEnsaio() != null ? a.getDataHoraEnsaio().toLocalDate() : null;
            if (temPeriodo && !emPeriodo(data, inicio, fim)) continue;
            if (data == null) continue;
            var ym = YearMonth.from(data);
            recebidoPorMes.merge(ym, a.getValorEntradaPago(), BigDecimal::add);
            despesasPorMes.merge(ym, financeCalculator.deslocamentoEfetivo(a), BigDecimal::add);
            despesasPorMes.merge(ym, repassePago(a.getId(), repasses), BigDecimal::add);
        }
        for (var i : indicacoes) {
            if (i.getStatus() != STATUS_COMISSAO_PAGA) continue;
            var data = dataEnsaioPorId.get(i.getAgendamentoId());
            if (data == null || (temPeriodo && !emPeriodo(data, inicio, fim))) continue;
            despesasPorMes.merge(YearMonth.from(data), i.getValorComissao(), BigDecimal::add);
        }
        for (var r : receitas) {
            if (r.getStatus() == StatusReceita.CANCELADO) continue;
            var data = r.getDataRecebimentoReal() != null
                ? r.getDataRecebimentoReal().toLocalDate()
                : r.getDataPrevisaoRecebimento();
            if (data == null) continue;
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
            List<Agendamento> agendamentos, List<Indicacao> indicacoes,
            FinanceCalculator.RepassesResumo repasses) {
        var custoPorTrabalho = custoDespesasPorTrabalho(despesas);
        var comissaoPorTrabalho = comissaoPorTrabalho(indicacoes);

        var receita = new HashMap<String, BigDecimal>();
        var liquido = new HashMap<String, BigDecimal>();
        for (var a : agendamentos) {
            var data = a.getDataHoraEnsaio() != null ? a.getDataHoraEnsaio().toLocalDate() : null;
            if (!emPeriodo(data, inicio, fim)) continue;
            var valor = a.getValorTotalFinal();
            var custo = custoPorTrabalho.getOrDefault(a.getId(), BigDecimal.ZERO)
                .add(financeCalculator.deslocamentoEfetivo(a))
                .add(comissaoPorTrabalho.getOrDefault(a.getId(), BigDecimal.ZERO))
                .add(repassePrevisto(a.getId(), repasses));
            receita.merge("ENSAIO", valor, BigDecimal::add);
            liquido.merge("ENSAIO", valor.subtract(custo), BigDecimal::add);
        }
        for (var r : receitas) {
            if (r.getStatus() == StatusReceita.CANCELADO) continue;
            var data = r.getDataPrevisaoRecebimento() != null
                ? r.getDataPrevisaoRecebimento()
                : (r.getDataRecebimentoReal() != null ? r.getDataRecebimentoReal().toLocalDate() : null);
            if (data == null || !emPeriodo(data, inicio, fim)) continue;
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
            List<Agendamento> agendamentos, List<Indicacao> indicacoes,
            FinanceCalculator.RepassesResumo repasses) {
        var custoPorTrabalho = custoDespesasPorTrabalho(despesas);
        var comissaoPorTrabalho = comissaoPorTrabalho(indicacoes);

        var resultado = new ArrayList<FinanceiroDashboardResponse.RentabilidadeTrabalho>();
        for (var a : agendamentos) {
            var data = a.getDataHoraEnsaio() != null ? a.getDataHoraEnsaio().toLocalDate() : null;
            if (!emPeriodo(data, inicio, fim)) continue;
            var valor = a.getValorTotalFinal();
            var custo = custoPorTrabalho.getOrDefault(a.getId(), BigDecimal.ZERO)
                .add(financeCalculator.deslocamentoEfetivo(a))
                .add(comissaoPorTrabalho.getOrDefault(a.getId(), BigDecimal.ZERO))
                .add(repassePrevisto(a.getId(), repasses));
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
            List<Despesa> despesas, List<Agendamento> agendamentos,
            FinanceCalculator.RepassesResumo repasses) {
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
            var repasse = repassePrevisto(a.getId(), repasses);
            if (repasse.signum() > 0) {
                lancamentos.add(new FinanceiroDashboardResponse.Lancamento(
                    a.getId().toString() + "-repasse", "DESPESA", data,
                    "Repasse — " + (pacoteNome != null ? pacoteNome : "Parceiros"),
                    "Repasse parceiros", repasse, "PENDENTE", "AGENDAMENTO"
                ));
            }
        }

        for (var r : receitas) {
            if (r.getStatus() == StatusReceita.CANCELADO) continue;
            var data = r.getDataPrevisaoRecebimento() != null
                ? r.getDataPrevisaoRecebimento()
                : (r.getDataRecebimentoReal() != null ? r.getDataRecebimentoReal().toLocalDate() : null);
            if (data == null || !emPeriodo(data, inicio, fim)) continue;
            var descricao = r.getDescricao() != null && !r.getDescricao().isBlank()
                ? r.getDescricao()
                : r.getClienteNome() + " — " + r.getTipoServico().label();
            lancamentos.add(new FinanceiroDashboardResponse.Lancamento(
                r.getId().toString(), "RECEITA", data, descricao,
                r.getTipoServico().label(), r.getValorFinal(),
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
            if (i.getStatus() == STATUS_COMISSAO_CANCELADA) continue;
            mapa.merge(i.getAgendamentoId(), i.getValorComissao(), BigDecimal::add);
        }
        return mapa;
    }

    private BigDecimal somarRepassesEnsaios(List<Agendamento> ensaios, Map<UUID, BigDecimal> repasses) {
        var total = BigDecimal.ZERO;
        for (var a : ensaios) {
            var valor = repasses.get(a.getId());
            if (valor != null) total = total.add(valor);
        }
        return total;
    }

    private BigDecimal repassePrevisto(UUID agendamentoId, FinanceCalculator.RepassesResumo repasses) {
        return repasses.previstos().getOrDefault(agendamentoId, BigDecimal.ZERO);
    }

    private BigDecimal repassePago(UUID agendamentoId, FinanceCalculator.RepassesResumo repasses) {
        return repasses.pagos().getOrDefault(agendamentoId, BigDecimal.ZERO);
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
        return FinanceiroQueryService.emPeriodo(data, inicio, fim);
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
        BigDecimal repasses,
        BigDecimal repassesPagos,
        BigDecimal despesasPeriodo,
        BigDecimal despesasPagas,
        int qtdTrabalhos,
        BigDecimal valorBruto,
        BigDecimal realizadas,
        BigDecimal aReceber
    ) {
        BigDecimal despesasTotais() {
            return deslocamentoEfetivo().add(comissao()).add(repasses()).add(despesasPeriodo());
        }

        BigDecimal liquidoPrevisto() {
            return valorBruto().subtract(despesasTotais());
        }

        BigDecimal liquidoRealizado() {
            return realizadas()
                .subtract(despesasPagas().add(comissaoPaga()).add(deslocamentoEfetivoPago()).add(repassesPagos()));
        }
    }
}
