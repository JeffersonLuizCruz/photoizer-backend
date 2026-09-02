package com.photoizer.crm.comissao.service;

import com.photoizer.crm.comissao.model.Indicacao;
import com.photoizer.crm.comissao.model.StatusIndicacao;
import com.photoizer.crm.comissao.repository.IndicacaoRepository;
import com.photoizer.crm.comissao.repository.projection.IndicadorComissaoProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Facade de leitura para dados de comissões.
 * Pattern: Query Service Facade — centraliza regras de consulta de comissões
 * no módulo dono, evitando que dashboard/financeiro/indicador acessem
 * IndicacaoRepository diretamente (violação Modulith).
 */
@Service
@Transactional(readOnly = true)
public class ComissaoQueryService {

    private final IndicacaoRepository indicacaoRepository;

    public ComissaoQueryService(IndicacaoRepository indicacaoRepository) {
        this.indicacaoRepository = indicacaoRepository;
    }

    /**
     * Retorna comissão total e paga por agendamento.
     * Exclui indicações CANCELADAS do total.
     */
    public Map<UUID, ComissaoResumo> obterComissaoPorAgendamentos(List<UUID> agendamentoIds) {
        var indicacoes = indicacaoRepository.findByAgendamentoIdIn(agendamentoIds);
        Map<UUID, BigDecimal> totalPorAgendamento = new HashMap<>();
        Map<UUID, BigDecimal> pagaPorAgendamento = new HashMap<>();

        for (var ind : indicacoes) {
            if (ind.getStatus() == StatusIndicacao.CANCELADA) continue;
            totalPorAgendamento.merge(ind.getAgendamentoId(), ind.getValorComissao(), BigDecimal::add);
            if (ind.getStatus() == StatusIndicacao.PAGA) {
                pagaPorAgendamento.merge(ind.getAgendamentoId(), ind.getValorComissao(), BigDecimal::add);
            }
        }

        Map<UUID, ComissaoResumo> resultado = new HashMap<>();
        for (var id : agendamentoIds) {
            resultado.put(id, new ComissaoResumo(
                totalPorAgendamento.getOrDefault(id, BigDecimal.ZERO),
                pagaPorAgendamento.getOrDefault(id, BigDecimal.ZERO)
            ));
        }
        return resultado;
    }

    /**
     * Retorna resumo de comissões agrupado por indicador, filtrado por IDs.
     * Usado pelo módulo indicador para listar indicadores com totais
     * em uma única query (resolve N+1).
     */
    public Map<UUID, IndicadorComissaoResumo> obterResumoPorIndicadores(List<UUID> indicadorIds) {
        if (indicadorIds == null || indicadorIds.isEmpty()) {
            return Map.of();
        }
        var projections = indicacaoRepository.findResumoByIndicadorIds(indicadorIds);
        Map<UUID, IndicadorComissaoResumo> resultado = new HashMap<>();
        for (IndicadorComissaoProjection p : projections) {
            resultado.put(p.getIndicadorId(), new IndicadorComissaoResumo(
                p.getTotalPendente(),
                p.getTotalPago(),
                p.getTotalIndicacoes()
            ));
        }
        return resultado;
    }

    public record ComissaoResumo(BigDecimal total, BigDecimal paga) {}

    public record IndicadorComissaoResumo(BigDecimal totalPendente, BigDecimal totalPago, int totalIndicacoes) {}
}
