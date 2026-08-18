package com.photoizer.crm.financeiro.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.comissao.model.Indicacao;
import com.photoizer.crm.comissao.repository.IndicacaoRepository;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.despesa.api.DespesaResponse;
import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.despesa.model.StatusDespesa;
import com.photoizer.crm.despesa.repository.DespesaRepository;
import com.photoizer.crm.indicador.service.IndicadorService;
import com.photoizer.crm.pacote.model.Pacote;
import com.photoizer.crm.pacote.repository.PacoteRepository;
import com.photoizer.crm.agenda.api.AgendamentoResponse;
import com.photoizer.crm.financeiro.api.FinanceiroPreviewResponse;
import com.photoizer.crm.financeiro.api.FinanceiroRelatoriosResponse;
import com.photoizer.crm.financeiro.api.FinanceiroResumoResponse;
import com.photoizer.crm.financeiro.api.FinanceiroTrabalhoResponse;
import com.photoizer.crm.financeiro.api.FluxoCaixaResponse;
import com.photoizer.crm.financeiro.model.FotoExtra;
import com.photoizer.crm.financeiro.model.Pagamento;
import com.photoizer.crm.financeiro.model.Receita;
import com.photoizer.crm.financeiro.model.StatusReceita;
import com.photoizer.crm.financeiro.model.TipoServico;
import com.photoizer.crm.financeiro.model.VideoExtra;
import com.photoizer.crm.financeiro.repository.FotoExtraRepository;
import com.photoizer.crm.financeiro.repository.PagamentoRepository;
import com.photoizer.crm.financeiro.repository.ReceitaRepository;
import com.photoizer.crm.financeiro.repository.VideoExtraRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class FinanceiroService {

    private final PagamentoRepository pagamentoRepository;
    private final FotoExtraRepository fotoExtraRepository;
    private final VideoExtraRepository videoExtraRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final PacoteRepository pacoteRepository;
    private final IndicacaoRepository indicacaoRepository;
    private final IndicadorService indicadorService;
    private final ConfiguracaoService configuracaoService;
    private final DespesaRepository despesaRepository;
    private final ReceitaRepository receitaRepository;
    private final AgendamentoFotografoRepository agendamentoFotografoRepository;

    public FinanceiroService(PagamentoRepository pagamentoRepository,
                             FotoExtraRepository fotoExtraRepository,
                             VideoExtraRepository videoExtraRepository,
                             AgendamentoRepository agendamentoRepository,
                             PacoteRepository pacoteRepository,
                             IndicacaoRepository indicacaoRepository,
                             IndicadorService indicadorService,
                             ConfiguracaoService configuracaoService,
                             DespesaRepository despesaRepository,
                             ReceitaRepository receitaRepository,
                             AgendamentoFotografoRepository agendamentoFotografoRepository) {
        this.pagamentoRepository = pagamentoRepository;
        this.fotoExtraRepository = fotoExtraRepository;
        this.videoExtraRepository = videoExtraRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.pacoteRepository = pacoteRepository;
        this.indicacaoRepository = indicacaoRepository;
        this.indicadorService = indicadorService;
        this.configuracaoService = configuracaoService;
        this.despesaRepository = despesaRepository;
        this.receitaRepository = receitaRepository;
        this.agendamentoFotografoRepository = agendamentoFotografoRepository;
    }

    @Transactional(readOnly = true)
    public FinanceiroPreviewResponse calcularPreview(UUID pacoteId, BigDecimal taxaDeslocamento) {
        var pacote = pacoteRepository.findById(pacoteId).orElseThrow();
        var taxa = taxaDeslocamento != null ? taxaDeslocamento : BigDecimal.ZERO;

        var percentualEntrada = configuracaoService.getValorDecimal("percentualEntrada", new BigDecimal("30.00"));
        var fatorEntrada = percentualEntrada.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        var valorTotal = pacote.getValorBase().add(taxa);
        var valorEntradaExigido = valorTotal.multiply(fatorEntrada)
            .setScale(2, RoundingMode.HALF_UP);
        var valorRestante = valorTotal.subtract(valorEntradaExigido);
        var valorTotalFinal = valorTotal;

        return new FinanceiroPreviewResponse(valorTotal, valorEntradaExigido, valorRestante, valorTotalFinal, percentualEntrada);
    }

    @Transactional(readOnly = true)
    public FinanceiroResumoResponse calcularResumo(LocalDateTime dataInicio, LocalDateTime dataFim) {
        var statusIgnorados = Set.of(StatusAgendamento.CANCELADO, StatusAgendamento.NO_SHOW);
        var statusPagamentoFinal = Set.of(
            StatusAgendamento.EM_EDICAO,
            StatusAgendamento.FOTOS_ENVIADAS_PARA_SELECAO,
            StatusAgendamento.FOTOS_ENTREGUES,
            StatusAgendamento.FINALIZADO
        );

        List<Agendamento> agendamentos;
        if (dataInicio != null && dataFim != null) {
            agendamentos = agendamentoRepository.findByDataBetween(dataInicio, dataFim, List.copyOf(statusIgnorados));
        } else {
            agendamentos = agendamentoRepository.findAll().stream()
                .filter(a -> !statusIgnorados.contains(a.getStatus()))
                .toList();
        }

        var totalEntradas = BigDecimal.ZERO;
        var totalFinal = BigDecimal.ZERO;
        var totalExtras = BigDecimal.ZERO;
        var faturamentoTotal = BigDecimal.ZERO;
        var deslocamento = BigDecimal.ZERO;
        var repasse = BigDecimal.ZERO;

        var repassesPorEnsaio = repassesPrevistosPorEnsaio();

        for (var a : agendamentos) {
            totalEntradas = totalEntradas.add(a.getValorEntradaPago());
            totalExtras = totalExtras.add(a.getValorExtras());
            faturamentoTotal = faturamentoTotal.add(a.getValorTotalFinal());
            repasse = repasse.add(repassesPorEnsaio.getOrDefault(a.getId(), BigDecimal.ZERO));

            if (!Boolean.TRUE.equals(a.getRepassarDeslocamento())) {
                var custo = a.getCustoDeslocamento() != null ? a.getCustoDeslocamento() : BigDecimal.ZERO;
                deslocamento = deslocamento.add(custo);
            }

            if (statusPagamentoFinal.contains(a.getStatus())) {
                totalFinal = totalFinal.add(a.getValorRestante());
            }
        }

        var ids = agendamentos.stream().map(Agendamento::getId).toList();
        var indicacoes = indicacaoRepository.findByAgendamentoIdIn(ids);
        var comissao = indicacoes.stream()
            .map(com.photoizer.crm.comissao.model.Indicacao::getValorComissao)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal despesasManuais;
        if (dataInicio != null && dataFim != null) {
            despesasManuais = despesaRepository.findByDataBetweenOrderByDataDesc(dataInicio.toLocalDate(), dataFim.toLocalDate())
                .stream().map(d -> d.getValor()).reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            despesasManuais = BigDecimal.ZERO;
        }

        return new FinanceiroResumoResponse(totalEntradas, totalFinal, totalExtras, faturamentoTotal, deslocamento, comissao, repasse, despesasManuais);
    }

    @Transactional(readOnly = true)
    public FinanceiroRelatoriosResponse calcularRelatorios(LocalDateTime dataInicio, LocalDateTime dataFim) {
        var statusIgnorados = Set.of(StatusAgendamento.CANCELADO, StatusAgendamento.NO_SHOW);

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

        var total = BigDecimal.ZERO;
        var entrada = BigDecimal.ZERO;
        var restante = BigDecimal.ZERO;
        var extras = BigDecimal.ZERO;
        var totalFinal = BigDecimal.ZERO;
        var repasse = BigDecimal.ZERO;

        var repassesPorEnsaio = repassesPrevistosPorEnsaio();

        for (var a : sorted) {
            total = total.add(a.getValorTotal());
            entrada = entrada.add(a.getValorEntradaPago());
            restante = restante.add(a.getValorRestante());
            extras = extras.add(a.getValorExtras());
            totalFinal = totalFinal.add(a.getValorTotalFinal());
            repasse = repasse.add(repassesPorEnsaio.getOrDefault(a.getId(), BigDecimal.ZERO));
        }

        var totais = new FinanceiroRelatoriosResponse.RelatoriosTotais(total, entrada, restante, extras, totalFinal, repasse);
        var responses = sorted.stream().map(AgendamentoResponse::of).toList();
        return new FinanceiroRelatoriosResponse(totais, responses, responses.size());
    }

    @Transactional(readOnly = true)
    public FinanceiroTrabalhoResponse resumoPorAgendamento(UUID agendamentoId) {
        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado: " + agendamentoId));

        var despesas = despesaRepository.findByAgendamentoIdOrderByDataDesc(agendamentoId);
        var pagamentos = pagamentoRepository.findByAgendamentoId(agendamentoId);

        var totalDespesas = despesas.stream()
            .map(Despesa::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var custoDeslocamento = Boolean.TRUE.equals(agendamento.getRepassarDeslocamento())
            ? BigDecimal.ZERO
            : (agendamento.getCustoDeslocamento() != null ? agendamento.getCustoDeslocamento() : BigDecimal.ZERO);
        var comissao = indicacaoRepository.findByAgendamentoIdIn(List.of(agendamentoId)).stream()
            .map(Indicacao::getValorComissao)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var custoTotal = totalDespesas.add(custoDeslocamento).add(comissao);
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

        var links = agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(agendamentoId);
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
                .map(DespesaResponse::of))
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
            despesas.stream().map(DespesaResponse::of).toList(),
            custosFotografoDespesas,
            pagamentos.stream().map(com.photoizer.crm.financeiro.api.PagamentoResponse::of).toList()
        );
    }

    @Transactional(readOnly = true)
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
                        : r.getClienteNome() + " — " + labelServico(r.getTipoServico());
                    itens.add(new FluxoCaixaResponse.FluxoCaixaItem(
                        r.getId(), "RECEITA", descricao, labelServico(r.getTipoServico()),
                        data, valor, r.getStatus().name(),
                        r.getAgendamentoId() != null ? "AGENDAMENTO" : "MANUAL"));
                }
            } else if (r.getStatus() == StatusReceita.PAGO_TOTAL
                && r.getDataRecebimentoReal() != null
                && emPeriodo(r.getDataRecebimentoReal().toLocalDate(), rangeInicio, rangeFim)) {
                entradasRealizadas = entradasRealizadas.add(r.getValorRecebido());
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

        var linksRepasse = agendamentoFotografoRepository
            .findByStatusWithAgendamento(RepasseStatus.PENDENTE);
        for (var link : linksRepasse) {
            var data = link.getAgendamento().getDataHoraEnsaio() != null
                ? link.getAgendamento().getDataHoraEnsaio().toLocalDate()
                : null;
            if (!emPeriodo(data, rangeInicio, rangeFim)) continue;
            var descricao = "Repasse — " + link.getFotografo().getNome()
                + " (" + link.getAgendamento().getCliente().getNome() + ")";
            itens.add(new FluxoCaixaResponse.FluxoCaixaItem(
                link.getId(), "DESPESA", descricao, "Repasse parceiros",
                data, link.getValorRepassar(), RepasseStatus.PENDENTE.name(), "AGENDAMENTO"));
        }
        var linksRepassePagos = agendamentoFotografoRepository
            .findByStatusWithAgendamento(RepasseStatus.PAGO);
        for (var link : linksRepassePagos) {
            var data = link.getDataPagamento() != null
                ? link.getDataPagamento().toLocalDate()
                : (link.getAgendamento().getDataHoraEnsaio() != null
                    ? link.getAgendamento().getDataHoraEnsaio().toLocalDate()
                    : null);
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
            var entradasRealizadas = BigDecimal.ZERO;
            var saidasRealizadas = BigDecimal.ZERO;
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
                entradasRealizadas, saidasRealizadas));
        }
        return resultado;
    }

    private boolean emPeriodo(LocalDate data, LocalDate inicio, LocalDate fim) {
        if (data == null) return false;
        return !data.isBefore(inicio) && !data.isAfter(fim);
    }

    private Map<UUID, BigDecimal> repassesPrevistosPorEnsaio() {
        var mapa = new HashMap<UUID, BigDecimal>();
        for (var linha : agendamentoFotografoRepository.sumRepassesAtivosPorAgendamento(RepasseStatus.CANCELADO)) {
            var agendamentoId = (UUID) linha[0];
            var valor = (BigDecimal) linha[2];
            mapa.merge(agendamentoId, valor, BigDecimal::add);
        }
        return mapa;
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

    public Pagamento registrarPagamento(UUID agendamentoId, Pagamento pagamento) {
        var agendamento = agendamentoRepository.findById(agendamentoId).orElseThrow();
        pagamento.setAgendamento(agendamento);

        var novoValorPago = agendamento.getValorEntradaPago().add(pagamento.getValor());
        agendamento.setValorEntradaPago(novoValorPago);
        agendamento.setValorRestante(agendamento.getValorTotalFinal().subtract(novoValorPago));

        if (agendamento.getValorRestante().compareTo(BigDecimal.ZERO) <= 0) {
            agendamento.setStatus(StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL);
        }

        agendamentoRepository.save(agendamento);
        return pagamentoRepository.save(pagamento);
    }

    public void registrarPagamentoExtraEcommerce(UUID agendamentoId, BigDecimal valor, UUID compraExtraId) {
        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado: " + agendamentoId));

        agendamento.setValorExtras(agendamento.getValorExtras().add(valor));
        agendamento.setValorTotalFinal(agendamento.getValorTotal().add(agendamento.getValorExtras()));
        agendamento.setValorEntradaPago(agendamento.getValorEntradaPago().add(valor));
        agendamento.setValorRestante(agendamento.getValorTotalFinal().subtract(agendamento.getValorEntradaPago()));
        agendamentoRepository.save(agendamento);

        var pagamento = Pagamento.builder()
            .agendamento(agendamento)
            .valor(valor)
            .dataPagamento(LocalDateTime.now())
            .compraExtraId(compraExtraId)
            .observacao("Fotos extras (e-commerce)")
            .build();
        pagamentoRepository.save(pagamento);
    }

    public FotoExtra adicionarFotoExtra(UUID agendamentoId, int quantidade, BigDecimal valorUnitario,
                                        String indicadorNome, String indicadorTelefone, UUID indicadorId) {
        var agendamento = agendamentoRepository.findById(agendamentoId).orElseThrow();

        var valorTotal = valorUnitario.multiply(BigDecimal.valueOf(quantidade));
        var fotoExtra = FotoExtra.builder()
            .agendamento(agendamento)
            .quantidade(quantidade)
            .valorUnitario(valorUnitario)
            .valorTotal(valorTotal)
            .build();

        agendamento.setValorExtras(agendamento.getValorExtras().add(valorTotal));
        agendamento.setValorTotalFinal(agendamento.getValorTotal().add(agendamento.getValorExtras()));
        agendamentoRepository.save(agendamento);

        criarComissaoSeNecessario(agendamentoId, indicadorNome, indicadorTelefone, indicadorId,
            "FOTO_EXTRA", valorTotal);

        return fotoExtraRepository.save(fotoExtra);
    }

    public VideoExtra adicionarVideoExtra(UUID agendamentoId, int quantidade, BigDecimal valorUnitario,
                                          String indicadorNome, String indicadorTelefone, UUID indicadorId) {
        var agendamento = agendamentoRepository.findById(agendamentoId).orElseThrow();

        var valorTotal = valorUnitario.multiply(BigDecimal.valueOf(quantidade));
        var videoExtra = VideoExtra.builder()
            .agendamento(agendamento)
            .quantidade(quantidade)
            .valorUnitario(valorUnitario)
            .valorTotal(valorTotal)
            .build();

        agendamento.setValorExtras(agendamento.getValorExtras().add(valorTotal));
        agendamento.setValorTotalFinal(agendamento.getValorTotal().add(agendamento.getValorExtras()));
        agendamentoRepository.save(agendamento);

        criarComissaoSeNecessario(agendamentoId, indicadorNome, indicadorTelefone, indicadorId,
            "VIDEO_EXTRA", valorTotal);

        return videoExtraRepository.save(videoExtra);
    }

    private void criarComissaoSeNecessario(UUID agendamentoId, String indicadorNome, String indicadorTelefone,
                                           UUID indicadorId, String origem, BigDecimal valorReferencia) {
        var nome = indicadorNome;
        var telefone = indicadorTelefone;
        UUID id = indicadorId;

        if ((nome == null || nome.isBlank()) && (telefone == null || telefone.isBlank()) && id == null) return;

        if (id == null && nome != null && !nome.isBlank() && telefone != null && !telefone.isBlank()) {
            var indicador = indicadorService.buscarOuCriar(nome, telefone);
            id = indicador.getId();
            nome = indicador.getNome();
            telefone = indicador.getTelefone();
        }

        if (id == null) return;

        var indicador = indicadorService.buscarPorId(id);
        var percentual = indicador.getPercentualComissao() != null
            ? indicador.getPercentualComissao()
            : configuracaoService.getValorDecimal("percentualComissao", BigDecimal.TEN);

        var comissao = valorReferencia.multiply(percentual).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        var indicacao = Indicacao.builder()
            .agendamentoId(agendamentoId)
            .indicadorId(id)
            .indicadorNome(nome)
            .indicadorTelefone(telefone)
            .origem(origem)
            .percentual(percentual)
            .valorReferencia(valorReferencia)
            .valorComissao(comissao)
            .status("PENDENTE")
            .build();

        indicacaoRepository.save(indicacao);
    }

    @Transactional(readOnly = true)
    public List<Pagamento> listarPagamentos(UUID agendamentoId) {
        return pagamentoRepository.findByAgendamentoId(agendamentoId);
    }

    public boolean isClienteBloqueado(UUID clienteId) {
        var agendamentos = agendamentoRepository.findAll();
        return agendamentos.stream()
            .filter(a -> a.getCliente().getId().equals(clienteId))
            .anyMatch(a -> a.getValorRestante().compareTo(BigDecimal.ZERO) > 0
                && a.getStatus() != StatusAgendamento.CANCELADO);
    }
}
