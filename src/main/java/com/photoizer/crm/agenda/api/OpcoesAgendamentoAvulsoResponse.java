package com.photoizer.crm.agenda.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OpcoesAgendamentoAvulsoResponse(
    List<ProfissionalOpcao> fotografos,
    BigDecimal percentualEntrada,
    BigDecimal taxaDeslocamentoPadrao
) {
    public record ProfissionalOpcao(UUID id, String nome) {}
}
