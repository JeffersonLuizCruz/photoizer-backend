package com.photoizer.crm.financeiro.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.cliente.model.Cliente;
import com.photoizer.crm.comissao.repository.IndicacaoRepository;
import com.photoizer.crm.despesa.repository.DespesaRepository;
import com.photoizer.crm.financeiro.repository.ReceitaRepository;
import com.photoizer.crm.financeiro.model.StatusReceita;
import com.photoizer.crm.financeiro.model.TipoServico;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinanceiroDashboardServiceTest {

    private final ReceitaRepository receitaRepository = mock(ReceitaRepository.class);
    private final DespesaRepository despesaRepository = mock(DespesaRepository.class);
    private final AgendamentoRepository agendamentoRepository = mock(AgendamentoRepository.class);
    private final IndicacaoRepository indicacaoRepository = mock(IndicacaoRepository.class);

    private FinanceiroDashboardService service;

    @BeforeEach
    void setUp() {
        service = new FinanceiroDashboardService(
            receitaRepository, despesaRepository, agendamentoRepository, indicacaoRepository);
        when(receitaRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(despesaRepository.findAll()).thenReturn(List.of());
        when(indicacaoRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void valorBrutoNaocontaCompraExtraSeparadamente() {
        var agendamento = Agendamento.builder()
            .id(UUID.randomUUID())
            .cliente(Cliente.builder().nome("Cliente Teste").build())
            .dataHoraEnsaio(LocalDateTime.of(2026, 6, 10, 10, 0))
            .valorEntradaPago(new BigDecimal("300.00"))
            .valorRestante(new BigDecimal("700.00"))
            .valorTotalFinal(new BigDecimal("1000.00"))
            .status(StatusAgendamento.REALIZADO)
            .build();
        when(agendamentoRepository.findAll()).thenReturn(List.of(agendamento));

        var resultado = service.calcular(
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
            null, StatusReceita.PENDENTE, null, null);

        var cards = resultado.cards();
        assertEquals(0, cards.valorBruto().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, cards.aReceber().compareTo(new BigDecimal("700.00")));
        assertEquals(0, cards.detalhamento().receitasEcommerce().compareTo(BigDecimal.ZERO));
    }

    @Test
    void ignoraAgendamentoForaDoPeriodo() {
        var agendamento = Agendamento.builder()
            .id(UUID.randomUUID())
            .cliente(Cliente.builder().nome("Cliente Teste").build())
            .dataHoraEnsaio(LocalDateTime.of(2025, 1, 1, 10, 0))
            .valorEntradaPago(new BigDecimal("300.00"))
            .valorRestante(new BigDecimal("700.00"))
            .valorTotalFinal(new BigDecimal("1000.00"))
            .status(StatusAgendamento.REALIZADO)
            .build();
        when(agendamentoRepository.findAll()).thenReturn(List.of(agendamento));

        var resultado = service.calcular(
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
            TipoServico.ENSAIO, StatusReceita.PENDENTE, null, null);

        assertEquals(0, resultado.cards().valorBruto().compareTo(BigDecimal.ZERO));
    }
}