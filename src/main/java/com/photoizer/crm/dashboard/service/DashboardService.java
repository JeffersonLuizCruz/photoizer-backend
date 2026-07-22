package com.photoizer.crm.dashboard.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.dashboard.api.DashboardMensalResponse;
import com.photoizer.crm.dashboard.api.DashboardMensalResponse.DadosMensais;
import com.photoizer.crm.dashboard.api.DashboardMensalResponse.ResumoMesAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

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

    public DashboardService(AgendamentoRepository agendamentoRepository) {
        this.agendamentoRepository = agendamentoRepository;
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

        var historico = new ArrayList<DadosMensais>();
        ResumoMesAtual resumoMesAtual = null;

        for (int i = meses - 1; i >= 0; i--) {
            var ym = mesAtual.minusMonths(i);
            var lista = porMes.getOrDefault(ym, List.of());

            var valorConfirmados = BigDecimal.ZERO;
            var valorFinalizados = BigDecimal.ZERO;
            var deslocamento = BigDecimal.ZERO;
            var entradasRecebidas = BigDecimal.ZERO;
            int qtdConfirmados = 0;
            int qtdFinalizados = 0;

            for (var a : lista) {
                var taxa = a.getTaxaDeslocamento() != null ? a.getTaxaDeslocamento() : BigDecimal.ZERO;
                deslocamento = deslocamento.add(taxa);

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

            var dados = new DadosMensais(
                ym.format(DateTimeFormatter.ofPattern("yyyy-MM")),
                lista.size(),
                valorConfirmados,
                valorFinalizados,
                deslocamento,
                entradasRecebidas,
                entradasRecebidas.subtract(deslocamento),
                valorConfirmados.subtract(deslocamento)
            );
            historico.add(dados);

            if (ym.equals(mesAtual)) {
                var saldoRestante = valorConfirmados.subtract(entradasRecebidas);
                var saldoLiquido = entradasRecebidas.subtract(deslocamento);
                var receitaProjetada = valorFinalizados.subtract(deslocamento);
                var liquidoPrevisto = valorConfirmados.subtract(deslocamento);

                resumoMesAtual = new ResumoMesAtual(
                    lista.size(),
                    qtdConfirmados,
                    valorConfirmados,
                    entradasRecebidas,
                    saldoRestante,
                    qtdFinalizados,
                    valorFinalizados,
                    deslocamento,
                    saldoLiquido,
                    receitaProjetada,
                    saldoLiquido,
                    liquidoPrevisto
                );
            }
        }

        return new DashboardMensalResponse(resumoMesAtual, historico);
    }
}
