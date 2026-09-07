package com.photoizer.crm.shared.port;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * PATTERN: Port (Hexagonal Architecture / Ports & Adapters)
 *
 * Porta de agregação de dados de repasses por agendamento.
 * Encapsula o carregamento de dados via repositório e a lógica de agregação.
 *
 * Motivo: O FinanceCalculator original recebia AgendamentoFotografoRepository
 * como parâmetro — acoplamento direto à infraestrutura de persistência.
 * Com esta porta, o adaptador encapsula a query e a agregação,
 * e o consumidor apenas chama carregarRepasses() sem conhecer o repositório.
 *
 * Adaptador: AgendamentoRepasseAdapter (no módulo agenda/adapter/),
 * que injeta AgendamentoFotografoRepository e executa a query.
 */
public interface RepasseAggregationPort {

    /**
     * Resumo de repasses por agendamento, separando previstos e pagos.
     */
    record RepassesResumo(Map<UUID, BigDecimal> previstos, Map<UUID, BigDecimal> pagos) {}

    /**
     * Carrega todos os repasses ativos (não cancelados), agregados por agendamento.
     */
    RepassesResumo carregarRepasses();
}
