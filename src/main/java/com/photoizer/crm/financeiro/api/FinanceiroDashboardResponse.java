package com.photoizer.crm.financeiro.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FinanceiroDashboardResponse(
    CardsResumo cards,
    List<DadoMensal> barraMensal,
    List<DespesaCategoriaDado> despesasPorCategoria,
    List<DadoLucroMensal> lucroMensal,
    List<RentabilidadeServico> rentabilidadePorServico,
    List<RentabilidadeTrabalho> rentabilidadePorTrabalho,
    List<Lancamento> ultimosLancamentos
) {
    public record CardsResumo(
        BigDecimal valorBruto,
        BigDecimal despesasTotais,
        BigDecimal liquidoPrevisto,
        BigDecimal liquidoRealizado,
        BigDecimal aReceber,
        BigDecimal margemLucro,
        BigDecimal ticketMedio,
        int qtdTrabalhos,
        VariacaoCards variacoes,
        Detalhamento detalhamento
    ) {}

    public record Detalhamento(
        BigDecimal recebido,
        BigDecimal entradaEnsaios,
        BigDecimal restanteEnsaios,
        BigDecimal receitasEcommerce,
        BigDecimal receitasAvulsas,
        BigDecimal comissao,
        BigDecimal deslocamento,
        BigDecimal repasses,
        BigDecimal despesas
    ) {}

    public record VariacaoCards(
        BigDecimal valorBruto,
        BigDecimal despesasTotais,
        BigDecimal liquidoPrevisto,
        BigDecimal liquidoRealizado
    ) {}

    public record DadoMensal(
        String mes,
        BigDecimal receitas,
        BigDecimal despesas
    ) {}

    public record DadoLucroMensal(
        String mes,
        BigDecimal liquido
    ) {}

    public record DespesaCategoriaDado(
        String categoria,
        String cor,
        BigDecimal valor
    ) {}

    public record RentabilidadeServico(
        String tipoServico,
        BigDecimal receita,
        BigDecimal liquido,
        BigDecimal margem
    ) {}

    public record RentabilidadeTrabalho(
        UUID agendamentoId,
        String clienteNome,
        String tipoServico,
        BigDecimal valorTrabalho,
        BigDecimal custoTrabalho,
        BigDecimal roi,
        BigDecimal margem
    ) {}

    public record Lancamento(
        String id,
        String tipo,
        LocalDate data,
        String descricao,
        String categoria,
        BigDecimal valor,
        String status,
        String origem
    ) {}
}
