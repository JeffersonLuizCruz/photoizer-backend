package com.photoizer.crm.agenda.adapter;

import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.shared.port.RepasseAggregationPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PATTERN: Adapter (Hexagonal Architecture / Ports & Adapters)
 *
 * Implementação de RepasseAggregationPort que encapsula o carregamento
 * de dados via AgendamentoFotografoRepository.
 *
 * O adaptador injeta o repositório do módulo agenda e executa a query
 * de agregação, retornando o resultado via porta definida em shared.
 *
 * Motivo: O FinanceCalculator original recebia o repositório como parâmetro
 * de método (acoplamento direto). Agora o adaptador encapsula essa
 * dependência e o consumidor apenas chama carregarRepasses().
 */
@Component
public class AgendamentoRepasseAdapter implements RepasseAggregationPort {

    private final AgendamentoFotografoRepository repository;

    public AgendamentoRepasseAdapter(AgendamentoFotografoRepository repository) {
        this.repository = repository;
    }

    @Override
    public RepassesResumo carregarRepasses() {
        Map<UUID, BigDecimal> previstos = new HashMap<>();
        Map<UUID, BigDecimal> pagos = new HashMap<>();

        var linhas = repository.sumRepassesAtivosPorAgendamento(RepasseStatus.CANCELADO);
        for (var linha : linhas) {
            var agendamentoId = linha.getAgendamentoId();
            var valor = linha.getValor();
            previstos.merge(agendamentoId, valor, BigDecimal::add);

            if (linha.getStatus() == RepasseStatus.PAGO) {
                pagos.merge(agendamentoId, valor, BigDecimal::add);
            }
        }

        return new RepassesResumo(previstos, pagos);
    }
}
