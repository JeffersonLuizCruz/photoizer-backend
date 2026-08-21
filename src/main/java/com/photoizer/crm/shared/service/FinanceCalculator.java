package com.photoizer.crm.shared.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.agenda.repository.projection.RepasseAggregation;
import com.photoizer.crm.comissao.model.StatusIndicacao;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Componente de cálculos financeiros compartilhados entre dashboard e financeiro.
 * Pattern: Utility Component — lógica pura de cálculo financeiro sem dependência de estado,
 * centralizada para evitar duplicação entre módulos consumidores.
 */
@Component
public class FinanceCalculator {

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

    private static final Set<StatusAgendamento> STATUS_CONFIRMADOS_OU_FINALIZADOS = Set.of(
        StatusAgendamento.CONFIRMADO,
        StatusAgendamento.REALIZADO,
        StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL,
        StatusAgendamento.EM_EDICAO,
        StatusAgendamento.SELECAO_DAS_FOTOS,
        StatusAgendamento.FOTOS_ENVIADAS_PARA_SELECAO,
        StatusAgendamento.FOTOS_ENTREGUES,
        StatusAgendamento.FINALIZADO
    );

    public Set<StatusAgendamento> statusIgnorados() {
        return STATUS_IGNORADOS;
    }

    public Set<StatusAgendamento> statusFinalizados() {
        return STATUS_FINALIZADOS;
    }

    public boolean isConfirmadoOuFinalizado(StatusAgendamento status) {
        return STATUS_CONFIRMADOS_OU_FINALIZADOS.contains(status);
    }

    public boolean isCanceladoOuNoShow(StatusAgendamento status) {
        return STATUS_IGNORADOS.contains(status);
    }

    /**
     * Calcula deslocamento efetivo: retorna ZERO quando repassarDeslocamento=true
     * (custo absorvido pelo cliente), senão retorna custoDeslocamento.
     */
    public BigDecimal deslocamentoEfetivo(Agendamento a) {
        if (Boolean.TRUE.equals(a.getRepassarDeslocamento())) return BigDecimal.ZERO;
        return a.getCustoDeslocamento() != null ? a.getCustoDeslocamento() : BigDecimal.ZERO;
    }

    /**
     * Agrega repasses ativos por agendamento, separando previstos e pagos.
     * Usa a projeção tipada RepasseAggregation em vez de Object[].
     */
    public RepassesResumo carregarRepasses(AgendamentoFotografoRepository repo) {
        Map<UUID, BigDecimal> previstos = new HashMap<>();
        Map<UUID, BigDecimal> pagos = new HashMap<>();
        var linhas = repo.sumRepassesAtivosPorAgendamento(RepasseStatus.CANCELADO);
        for (var linha : linhas) {
            var agendamentoId = linha.getAgendamentoId();
            var status = linha.getStatus();
            var valor = linha.getValor();
            previstos.merge(agendamentoId, valor, BigDecimal::add);
            if (status == RepasseStatus.PAGO) {
                pagos.merge(agendamentoId, valor, BigDecimal::add);
            }
        }
        return new RepassesResumo(previstos, pagos);
    }

    public record RepassesResumo(Map<UUID, BigDecimal> previstos, Map<UUID, BigDecimal> pagos) {}
}
