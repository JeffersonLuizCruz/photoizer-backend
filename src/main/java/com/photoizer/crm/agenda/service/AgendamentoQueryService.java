package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.shared.service.FinanceCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Facade de leitura para dados de agendamentos.
 * Pattern: Query Service Facade — expõe operações de consulta do módulo agenda
 * para outros módulos (dashboard, financeiro) sem expor o repositório diretamente.
 * Centraliza regras de filtro e cálculo no módulo dono.
 */
@Service
@Transactional(readOnly = true)
public class AgendamentoQueryService {

    private final AgendamentoRepository agendamentoRepository;
    private final AgendamentoFotografoRepository agendamentoFotografoRepository;
    private final FinanceCalculator financeCalculator;

    public AgendamentoQueryService(AgendamentoRepository agendamentoRepository,
                                   AgendamentoFotografoRepository agendamentoFotografoRepository,
                                   FinanceCalculator financeCalculator) {
        this.agendamentoRepository = agendamentoRepository;
        this.agendamentoFotografoRepository = agendamentoFotografoRepository;
        this.financeCalculator = financeCalculator;
    }

    /**
     * Retorna agendamentos ativos no período (exclui CANCELADO e NO_SHOW).
     */
    public List<Agendamento> obterPorPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        return agendamentoRepository.findByDataBetween(inicio, fim,
            List.copyOf(financeCalculator.statusIgnorados()));
    }

    /**
     * Conta agendamentos no período para KPI.
     */
    public long countPorPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        return agendamentoRepository.countByDataHoraEnsaioBetween(inicio, fim);
    }

    /**
     * Conta total de agendamentos (todos os status) para cálculo de taxa de conversão.
     */
    public long countTotal() {
        return agendamentoRepository.count();
    }

    /**
     * Retorna agendamentos por IDs (usado para top clientes ecommerce).
     */
    public List<Agendamento> obterPorIds(List<UUID> ids) {
        return agendamentoRepository.findAllById(ids);
    }

    /**
     * Calcula receita total do período somando valorTotalFinal de agendamentos
     * com status confirmado/finalizado. Query SQL via findByDataBetween + filtro em memória
     * apenas para status (necessário pois status é enum complexo).
     */
    public BigDecimal calcularReceitaPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        return obterPorPeriodo(inicio, fim).stream()
            .filter(a -> financeCalculator.isConfirmadoOuFinalizado(a.getStatus()))
            .map(a -> a.getValorTotalFinal() != null ? a.getValorTotalFinal() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Retorna o repositório de repasses para uso pelo FinanceCalculator.
     */
    public AgendamentoFotografoRepository repasseRepository() {
        return agendamentoFotografoRepository;
    }
}
