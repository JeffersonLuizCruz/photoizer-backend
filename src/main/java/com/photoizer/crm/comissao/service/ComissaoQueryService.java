package com.photoizer.crm.comissao.service;

import com.photoizer.crm.comissao.model.Indicacao;
import com.photoizer.crm.comissao.model.StatusIndicacao;
import com.photoizer.crm.comissao.repository.IndicacaoRepository;
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
 * no módulo dono, evitando que dashboard/financeiro acessem IndicacaoRepository diretamente.
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

    public record ComissaoResumo(BigDecimal total, BigDecimal paga) {}
}
