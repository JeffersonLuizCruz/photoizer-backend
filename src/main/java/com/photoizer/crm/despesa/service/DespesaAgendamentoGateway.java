package com.photoizer.crm.despesa.service;

import java.util.UUID;

/**
 * Porta/ACL para validação de existência de agendamento.
 *
 * Design Pattern: Facade / Port & Adapter (Hexagonal Architecture)
 *
 * Motivo: O módulo despesa não deveria importar AgendamentoRepository do agenda
 * diretamente — isso viola o princípio de módulos do Spring Modulith (DEBT.md §7.4).
 * Esta interface define um contrato (porta) que o módulo agenda implementa (adapter),
 * mantendo o fluxo de dependência correto: despesa → porta ← agenda.
 *
 * Benefícios:
 * - Remove acoplamento direto despesa → agenda.repository
 * - Facilita testes unitários (mock da porta)
 * - Permite trocar a implementação sem afetar o módulo despesa
 * - Alinhado com o padrão já usado em outros módulos (ex: dashboard facades)
 */
public interface DespesaAgendamentoGateway {

    boolean existsById(UUID agendamentoId);
}
