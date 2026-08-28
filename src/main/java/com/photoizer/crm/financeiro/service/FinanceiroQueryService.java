package com.photoizer.crm.financeiro.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.comissao.model.Indicacao;
import com.photoizer.crm.comissao.model.StatusIndicacao;
import com.photoizer.crm.comissao.repository.IndicacaoRepository;
import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.despesa.api.DespesaMapper;
import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.despesa.model.StatusDespesa;
import com.photoizer.crm.despesa.repository.DespesaRepository;
import com.photoizer.crm.financeiro.api.FinanceiroPreviewResponse;
import com.photoizer.crm.financeiro.api.FinanceiroRelatoriosResponse;
import com.photoizer.crm.financeiro.api.FinanceiroResumoResponse;
import com.photoizer.crm.financeiro.api.FinanceiroTrabalhoResponse;
import com.photoizer.crm.financeiro.api.FluxoCaixaResponse;
import com.photoizer.crm.financeiro.api.PagamentoResponse;
import com.photoizer.crm.financeiro.api.RelatorioAgendamentoItem;
import com.photoizer.crm.financeiro.exception.AgendamentoNaoEncontradoParaFinanceiroException;
import com.photoizer.crm.financeiro.exception.PacoteNaoEncontradoParaPreviewException;
import com.photoizer.crm.financeiro.model.StatusReceita;
import com.photoizer.crm.financeiro.repository.PagamentoRepository;
import com.photoizer.crm.financeiro.repository.ReceitaRepository;
import com.photoizer.crm.pacote.repository.PacoteRepository;
import com.photoizer.crm.shared.service.FinanceCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service de queries financeiras (read-only).
 *
 * Pattern: Query Service — Separa operações de leitura das de escrita (CQRS leve).
 * Usa FinanceCalculator do shared para cálculos de repasse (elimina duplicação).
 * Usa queries SQL nos repositories em vez de findAll() + streams.
 */
@Service
@Transactional(readOnly = true)
public class FinanceiroQueryService {

    private static final Set<StatusAgendamento> STATUS_PAGAMENTO_FINAL = Set.of(
        StatusAgendamento.EM_EDICAO,
        StatusAgendamento.FOTOS_ENVIADAS_PARA_SELECAO,
        StatusAgendamento.FOTOS_ENTREGUES,
        StatusAgendamento.FINALIZADO
    );

    private final AgendamentoRepository agendamentoRepository;
    private final AgendamentoFotografoRepository agendamentoFotografoRepository;
    private final PacoteRepository pacoteRepository;
    private final IndicacaoRepository indicacaoRepository;
    private final DespesaRepository despesaRepository;
    private final ReceitaRepository receitaRepository;
    private final PagamentoRepository pagamentoRepository;
    private final ConfiguracaoService configuracaoService;
    private final FinanceCalculator financeCalculator;
    private final DespesaMapper despesaMapper;

    public FinanceiroQueryService(AgendamentoRepository agendamentoRepository,
                                  AgendamentoFotografoRepository agendamentoFotografoRepository,
                                  PacoteRepository pacoteRepository,
                                  IndicacaoRepository indicacaoRepository,
                                  DespesaRepository despesaRepository,
                                  ReceitaRepository receitaRepository,
                                  PagamentoRepository pagamentoRepository,
                                  ConfiguracaoService configuracaoService,
                                  FinanceCalculator financeCalculator,
                                  DespesaMapper despesaMapper) {
        this.agendamentoRepository = agendamentoRepository;
        this.agendamentoFotografoRepository = agendamentoFotografoRepository;
        this.pacoteRepository = pacoteRepository;
        this.indicacaoRepository = indicacaoRepository;
        this.despesaRepository = despesaRepository;
        this.receitaRepository = receitaRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.configuracaoService = configuracaoService;
        this.financeCalculator = financeCalculator;
        this.despesaMapper = despesaMapper;
    }

    public FinanceiroPreviewResponse calcularPreview(UUID pacoteId, BigDecimal taxaDeslocamento) {
        var pacote = pacoteRepository.findById(pacoteId)
            .orElseThrow(() -> new PacoteNaoEncontradoParaPreviewException(pacoteId));
        var taxa = taxaDeslocamento != null ? taxaDeslocamento : BigDecimal.ZERO;

        var percentualEntrada = configuracaoService.getValorDecimal(ConfigKey.PERCENTUAL_ENTRADA);
        var fatorEntrada = percentualEntrada.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        var valorTotal = pacote.getValorBase().add(taxa);
        var valorEntradaExigido = valorTotal.multiply(fatorEntrada)
            .setScale(2, RoundingMode.HALF_UP);
        var valorRestante = valorTotal.subtract(valorEntradaExigido);

        return new FinanceiroPreviewResponse(valorTotal, valorEntradaExigido, valorRestante, valorTotal, percentualEntrada);
    }

    public FinanceiroResumoResponse calcularResumo(LocalDateTime dataInicio, LocalDateTime dataFim) {
        var statusIgnorados = financeCalculator.statusIgnorados();
        List<Agendamento> agendamentos;
        if (dataInicio != null && dataFim != null) {
            agendamentos = agendamentoRepository.findByDataBetween(dataInicio, dataFim, List.copyOf(statusIgnorados));
        } else {
            agendamentos = agendamentoRepository.findAll().stream()
                .filter(a -> !statusIgnorados.contains(a.getStatus()))
                .toList();
        }

        var repasses = financeCalculator.carregarRepasses(agendamentoFotografoRepository);

        var totalEntradas = BigDecimal.ZERO;
        var totalFinal = BigDecimal.ZERO;
        var totalExtras = BigDecimal.ZERO;
        var faturamentoTotal = BigDecimal.ZERO;
        var deslocamento = BigDecimal.ZERO;
        var repasse = BigDecimal.ZERO;

        for (var a : agendamentos) {
            totalEntradas = totalEntradas.add(a.getValorEntradaPago());
            totalExtras = totalExtras.add(a.getValorExtras());
            faturamentoTotal = faturamentoTotal.add(a.getValorTotalFinal());
            repasse = repasse.add(repasses.previstos().getOrDefault(a.getId(), BigDecimal.ZERO));
            deslocamento = deslocamento.add(financeCalculator.deslocamentoEfetivo(a));

            if (a.getValorRestante().compareTo(BigDecimal.ZERO) > 0) {
                totalFinal = totalFinal.add(a.getValorRestante());
            }
        }

        var ids = agendamentos.stream().map(Agendamento::getId).toList();
        var comissao = indicacaoRepository.sumValorComissaoByAgendamentoIdInAndStatusNot(ids, StatusIndicacao.CANCELADA);

        BigDecimal despesasManuais;
        if (dataInicio != null && dataFim != null) {
            despesasManuais = despesaRepository.sumValorByDataBetween(dataInicio.toLocalDate(), dataFim.toLocalDate());
        } else {
            despesasManuais = BigDecimal.ZERO;
        }

        return new FinanceiroResumoResponse(totalEntradas, totalFinal, totalExtras, faturamentoTotal, deslocamento, comissao, repasse, despesasManuais);
    }

    public FinanceiroRelatoriosResponse calcularRelatorios(LocalDateTime dataInicio, LocalDateTime dataFim) {
        var statusIgnorados = financeCalculator.statusIgnorados();
        List<Agendamento> agendamentos;
        if (dataInicio != null && dataFim != null) {
            agendamentos = agendamentoRepository.findByDataBetween(dataInicio, dataFim, List.copyOf(statusIgnorados));
        } else {
            agendamentos = agendamentoRepository.findAll().stream()
                .filter(a -> !statusIgnorados.contains(a.getStatus()))
                .toList();
        }

        var sorted = agendamentos.stream()
            .sorted(Comparator.comparing(Agendamento::getDataHoraEnsaio).reversed())
            .toList();

        var repasses = financeCalculator.carregarRepasses(agendamentoFotografoRepository);

        var total = BigDecimal.ZERO;
        var entrada = BigDecimal.ZERO;
        var restante = BigDecimal.ZERO;
        var extras = BigDecimal.ZERO;
        var totalFinal = BigDecimal.ZERO;
        var repasse = BigDecimal.ZERO;

        for (var a : sorted) {
            total = total.add(a.getValorTotal());
            entrada = entrada.add(a.getValorEntradaPago());
            restante = restante.add(a.getValorRestante());
            extras = extras.add(a.getValorExtras());
            totalFinal = totalFinal.add(a.getValorTotalFinal());
            repasse = repasse.add(repasses.previstos().getOrDefault(a.getId(), BigDecimal.ZERO));
        }

        var ids = sorted.stream().map(Agendamento::getId).toList();
        var comissao = ids.isEmpty() ? BigDecimal.ZERO
            : indicacaoRepository.sumValorComissaoByAgendamentoIdInAndStatusNot(ids, StatusIndicacao.CANCELADA);

        var totais = new FinanceiroRelatoriosResponse.RelatoriosTotais(total, entrada, restante, extras, totalFinal, repasse, comissao);
        var responses = sorted.stream()
            .map(RelatorioAgendamentoItem::of)
            .toList();
        return new FinanceiroRelatoriosResponse(totais, responses, responses.size());
    }

    public FinanceiroTrabalhoResponse resumoPorAgendamento(UUID agendamentoId) {
        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new AgendamentoNaoEncontradoParaFinanceiroException(agendamentoId));

        var despesas = despesaRepository.findByAgendamentoIdOrderByDataDesc(agendamentoId);
        var pagamentos = pagamentoRepository.findByAgendamentoId(agendamentoId);

        var totalDespesas = despesas.stream()
            .map(Despesa::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var custoDeslocamento = financeCalculator.deslocamentoEfetivo(agendamento);
        var comissao = indicacaoRepository.findByAgendamentoIdIn(List.of(agendamentoId)).stream()
            .map(Indicacao::getValorComissao)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var links = agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(agendamentoId);
        var totalRepasses = links.stream()
            .map(af -> af.getValorRepassar() != null ? af.getValorRepassar() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var custoTotal = totalDespesas.add(custoDeslocamento).add(comissao).add(totalRepasses);
        var valorCobrado = agendamento.getValorTotalFinal();

        var totalRecebido = agendamento.getValorEntradaPago() != null
            ? agendamento.getValorEntradaPago()
            : BigDecimal.ZERO;

        var saldoDevedor = valorCobrado.subtract(totalRecebido);
        var statusPagamento = saldoDevedor.compareTo(BigDecimal.ZERO) <= 0
            ? "PAGO"
            : (totalRecebido.signum() > 0 ? "PARCIAL" : "PENDENTE");

        var lucroBruto = valorCobrado.subtract(custoTotal);
        var margemLucro = valorCobrado.signum() > 0
            ? lucroBruto.multiply(BigDecimal.valueOf(100)).divide(valorCobrado, 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        var fotosInfo = links.stream()
            .map(af -> {
                var custosF = despesas.stream()
                    .filter(d -> af.getFotografo().getId().equals(d.getFotografoId()))
                    .map(Despesa::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                return new FinanceiroTrabalhoResponse.FotografoInfo(
                    af.getFotografo().getId(),
                    af.getFotografo().getNome(),
                    custosF,
                    af.getValorRepassar(),
                    af.getStatus(),
                    af.getDataPagamento(),
                    af.getTipoValor() != null ? af.getTipoValor() : com.photoizer.crm.shared.model.TipoRepasse.FIXO,
                    af.getPercentual(),
                    af.getPapelParceiro()
                );
            })
            .toList();

        var totalCustosFotografo = fotosInfo.stream()
            .map(FinanceiroTrabalhoResponse.FotografoInfo::custos)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var custosFotografoDespesas = fotosInfo.stream()
            .flatMap(fi -> despesas.stream()
                .filter(d -> fi.fotografoId().equals(d.getFotografoId()))
                .map(despesaMapper::toResponse))
            .toList();

        return new FinanceiroTrabalhoResponse(
            agendamentoId,
            agendamento.getCliente().getNome(),
            agendamento.getPacote() != null ? agendamento.getPacote().getNome() : null,
            valorCobrado,
            agendamento.getValorEntradaPago() != null ? agendamento.getValorEntradaPago() : BigDecimal.ZERO,
            saldoDevedor,
            totalRecebido,
            statusPagamento,
            totalDespesas,
            custoDeslocamento,
            comissao,
            custoTotal,
            lucroBruto,
            margemLucro,
            fotosInfo,
            agendamento.getValorPartilhaGlobal(),
            agendamento.getValorLucroCrm(),
            totalCustosFotografo,
            despesas.stream().map(despesaMapper::toResponse).toList(),
            custosFotografoDespesas,
            pagamentos.stream().map(PagamentoResponse::of).toList()
        );
    }

    public FluxoCaixaResponse calcularFluxoCaixa(LocalDate inicio, LocalDate fim, String visao) {
        var rangeInicio = inicio != null ? inicio : LocalDate.now();
        var rangeFim = fim != null ? fim : rangeInicio.plusMonths(3);
        boolean semanal = "SEMANAL".equalsIgnoreCase(visao);

        var receitas = receitaRepository.findAll();
        var despesas = despesaRepository.findAll();

        var itens = new ArrayList<FluxoCaixaResponse.FluxoCaixaItem>();
        var entradasRealizadas = BigDecimal.ZERO;
        var saidasRealizadas = BigDecimal.ZERO;

        for (var r : receitas) {
            if (r.getStatus() == StatusReceita.CANCELADO) continue;
            if (r.getStatus() == StatusReceita.PENDENTE || r.getStatus() == StatusReceita.PAGO_PARCIAL) {
                var data = r.getDataPrevisaoRecebimento();
                if (data != null && emPeriodo(data, rangeInicio, rangeFim)) {
                    var valor = r.getValorFinal().subtract(r.getValorRecebido());
                    var descricao = r.getDescricao() != null && !r.getDescricao().isBlank()
                        ? r.getDescricao()
                        : r.getClienteNome() + " — " + r.getTipoServico().label();
                    itens.add(new FluxoCaixaResponse.FluxoCaixaItem(
                        r.getId(), "RECEITA", descricao, r.getTipoServico().label(),
                        data, valor, r.getStatus().name(),
                        r.getAgendamentoId() != null ? "AGENDAMENTO" : "MANUAL"));
                }
            } else if (r.getStatus() == StatusReceita.PAGO_TOTAL) {
                var dataRef = r.getDataRecebimentoReal() != null
                    ? r.getDataRecebimentoReal().toLocalDate()
                    : r.getDataPrevisaoRecebimento();
                if (dataRef != null && emPeriodo(dataRef, rangeInicio, rangeFim)) {
                    entradasRealizadas = entradasRealizadas.add(r.getValorRecebido());
                }
            }
        }

        for (var d : despesas) {
            if (d.getStatus() == StatusDespesa.PENDENTE && emPeriodo(d.getData(), rangeInicio, rangeFim)) {
                itens.add(new FluxoCaixaResponse.FluxoCaixaItem(
                    d.getId(), "DESPESA", d.getDescricao(),
                    d.getCategoria() != null ? d.getCategoria() : "Outros",
                    d.getData(), d.getValor(), d.getStatus().name(),
                    d.getGeradaDeId() != null ? "RECORRENTE" : "MANUAL"));
            } else if (d.getStatus() == StatusDespesa.PAGO && emPeriodo(d.getData(), rangeInicio, rangeFim)) {
                saidasRealizadas = saidasRealizadas.add(d.getValor());
            }
        }

        var linksRepasse = agendamentoFotografoRepository.findByStatusWithAgendamento(RepasseStatus.PENDENTE);
        for (var link : linksRepasse) {
            var data = link.getAgendamento().getDataHoraEnsaio() != null
                ? link.getAgendamento().getDataHoraEnsaio().toLocalDate()
                : LocalDate.now();
            if (!emPeriodo(data, rangeInicio, rangeFim)) continue;
            var descricao = "Repasse — " + link.getFotografo().getNome()
                + " (" + link.getAgendamento().getCliente().getNome() + ")";
            itens.add(new FluxoCaixaResponse.FluxoCaixaItem(
                link.getId(), "DESPESA", descricao, "Repasse parceiros",
                data, link.getValorRepassar(), RepasseStatus.PENDENTE.name(), "AGENDAMENTO"));
        }
        var linksRepassePagos = agendamentoFotografoRepository.findByStatusWithAgendamento(RepasseStatus.PAGO);
        for (var link : linksRepassePagos) {
            var data = link.getDataPagamento() != null
                ? link.getDataPagamento().toLocalDate()
                : (link.getAgendamento().getDataHoraEnsaio() != null
                    ? link.getAgendamento().getDataHoraEnsaio().toLocalDate()
                    : LocalDate.now());
            if (!emPeriodo(data, rangeInicio, rangeFim)) continue;
            saidasRealizadas = saidasRealizadas.add(link.getValorRepassar());
        }

        var buckets = montarBucketsFluxo(rangeInicio, rangeFim, semanal, itens);

        var entradasPrevistasTotal = itens.stream()
            .filter(i -> "RECEITA".equals(i.tipo()))
            .map(FluxoCaixaResponse.FluxoCaixaItem::valor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var saidasPrevistasTotal = itens.stream()
            .filter(i -> "DESPESA".equals(i.tipo()))
            .map(FluxoCaixaResponse.FluxoCaixaItem::valor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var saldoProjetadoFinal = entradasPrevistasTotal.subtract(saidasPrevistasTotal);

        itens.sort(Comparator.comparing(FluxoCaixaResponse.FluxoCaixaItem::data));

        return new FluxoCaixaResponse(
            rangeInicio, rangeFim, semanal ? "SEMANAL" : "MENSAL",
            entradasRealizadas, saidasRealizadas,
            entradasPrevistasTotal, saidasPrevistasTotal, saldoProjetadoFinal,
            buckets, itens);
    }

    public boolean isClienteBloqueado(UUID clienteId) {
        return agendamentoRepository.existsByClienteIdWithSaldoDevedor(clienteId);
    }

    private List<FluxoCaixaResponse.FluxoCaixaBucket> montarBucketsFluxo(
            LocalDate inicio, LocalDate fim, boolean semanal,
            List<FluxoCaixaResponse.FluxoCaixaItem> itens) {
        var limites = new ArrayList<LocalDate[]>();
        if (semanal) {
            var cursor = inicio.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (cursor.isBefore(inicio)) cursor = cursor.plusWeeks(1);
            while (!cursor.isAfter(fim)) {
                limites.add(new LocalDate[]{cursor, cursor.plusDays(6)});
                cursor = cursor.plusWeeks(1);
            }
        } else {
            var ym = YearMonth.from(inicio);
            while (!ym.isAfter(YearMonth.from(fim))) {
                limites.add(new LocalDate[]{ym.atDay(1), ym.atEndOfMonth()});
                ym = ym.plusMonths(1);
            }
        }

        var resultado = new ArrayList<FluxoCaixaResponse.FluxoCaixaBucket>();
        var acumulado = BigDecimal.ZERO;
        for (var l : limites) {
            var bInicio = l[0].isBefore(inicio) ? inicio : l[0];
            var bFim = l[1].isAfter(fim) ? fim : l[1];
            if (bInicio.isAfter(bFim)) continue;

            var entradas = BigDecimal.ZERO;
            var saidas = BigDecimal.ZERO;
            for (var i : itens) {
                if (!emPeriodo(i.data(), bInicio, bFim)) continue;
                if ("RECEITA".equals(i.tipo())) entradas = entradas.add(i.valor());
                else saidas = saidas.add(i.valor());
            }
            var saldoPeriodo = entradas.subtract(saidas);
            acumulado = acumulado.add(saldoPeriodo);

            String rotulo;
            if (semanal) {
                rotulo = bInicio.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));
            } else {
                rotulo = YearMonth.from(bInicio).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            }
            resultado.add(new FluxoCaixaResponse.FluxoCaixaBucket(
                rotulo, bInicio, bFim, entradas, saidas, saldoPeriodo, acumulado,
                BigDecimal.ZERO, BigDecimal.ZERO));
        }
        return resultado;
    }

    static boolean emPeriodo(LocalDate data, LocalDate inicio, LocalDate fim) {
        if (data == null) return false;
        return !data.isBefore(inicio) && !data.isAfter(fim);
    }
}
