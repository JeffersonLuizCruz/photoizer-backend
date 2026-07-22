package com.photoizer.crm.financeiro.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.Pacote;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.agenda.repository.PacoteRepository;
import com.photoizer.crm.agenda.api.AgendamentoResponse;
import com.photoizer.crm.financeiro.api.FinanceiroPreviewResponse;
import com.photoizer.crm.financeiro.api.FinanceiroRelatoriosResponse;
import com.photoizer.crm.financeiro.api.FinanceiroResumoResponse;
import com.photoizer.crm.financeiro.model.FotoExtra;
import com.photoizer.crm.financeiro.model.Pagamento;
import com.photoizer.crm.financeiro.repository.FotoExtraRepository;
import com.photoizer.crm.financeiro.repository.PagamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class FinanceiroService {

    private static final BigDecimal PERCENTUAL_ENTRADA = BigDecimal.valueOf(0.3);

    private final PagamentoRepository pagamentoRepository;
    private final FotoExtraRepository fotoExtraRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final PacoteRepository pacoteRepository;

    public FinanceiroService(PagamentoRepository pagamentoRepository,
                             FotoExtraRepository fotoExtraRepository,
                             AgendamentoRepository agendamentoRepository,
                             PacoteRepository pacoteRepository) {
        this.pagamentoRepository = pagamentoRepository;
        this.fotoExtraRepository = fotoExtraRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.pacoteRepository = pacoteRepository;
    }

    @Transactional(readOnly = true)
    public FinanceiroPreviewResponse calcularPreview(UUID pacoteId, BigDecimal taxaDeslocamento) {
        var pacote = pacoteRepository.findById(pacoteId).orElseThrow();
        var taxa = taxaDeslocamento != null ? taxaDeslocamento : BigDecimal.ZERO;

        var valorTotal = pacote.getValorBase().add(taxa);
        var valorEntradaExigido = pacote.getValorBase().multiply(PERCENTUAL_ENTRADA);
        var valorRestante = valorTotal.subtract(valorEntradaExigido);
        var valorTotalFinal = valorTotal;

        return new FinanceiroPreviewResponse(valorTotal, valorEntradaExigido, valorRestante, valorTotalFinal);
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

        for (var a : agendamentos) {
            totalEntradas = totalEntradas.add(a.getValorEntradaPago());
            totalExtras = totalExtras.add(a.getValorExtras());
            faturamentoTotal = faturamentoTotal.add(a.getValorTotalFinal());

            if (statusPagamentoFinal.contains(a.getStatus())) {
                totalFinal = totalFinal.add(a.getValorRestante());
            }
        }

        return new FinanceiroResumoResponse(totalEntradas, totalFinal, totalExtras, faturamentoTotal);
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

        for (var a : sorted) {
            total = total.add(a.getValorTotal());
            entrada = entrada.add(a.getValorEntradaPago());
            restante = restante.add(a.getValorRestante());
            extras = extras.add(a.getValorExtras());
            totalFinal = totalFinal.add(a.getValorTotalFinal());
        }

        var totais = new FinanceiroRelatoriosResponse.RelatoriosTotais(total, entrada, restante, extras, totalFinal);
        var responses = sorted.stream().map(AgendamentoResponse::of).toList();
        return new FinanceiroRelatoriosResponse(totais, responses, responses.size());
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

    public FotoExtra adicionarFotoExtra(UUID agendamentoId, int quantidade, BigDecimal valorUnitario) {
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

        return fotoExtraRepository.save(fotoExtra);
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
