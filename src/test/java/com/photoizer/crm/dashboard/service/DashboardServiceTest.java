package com.photoizer.crm.dashboard.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.agenda.repository.projection.RepasseAggregation;
import com.photoizer.crm.agenda.service.AgendamentoQueryService;
import com.photoizer.crm.cliente.service.ClienteQueryService;
import com.photoizer.crm.comissao.model.Indicacao;
import com.photoizer.crm.comissao.model.StatusIndicacao;
import com.photoizer.crm.comissao.service.ComissaoQueryService;
import com.photoizer.crm.dashboard.api.DashboardMensalResponse;
import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.despesa.model.StatusDespesa;
import com.photoizer.crm.despesa.service.DespesaQueryService;
import com.photoizer.crm.ecommerce.service.EcommerceQueryService;
import com.photoizer.crm.financeiro.model.Receita;
import com.photoizer.crm.financeiro.model.StatusReceita;
import com.photoizer.crm.financeiro.model.TipoServico;
import com.photoizer.crm.financeiro.service.ReceitaQueryService;
import com.photoizer.crm.shared.service.FinanceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private final AgendamentoQueryService agendamentoQueryService = mock(AgendamentoQueryService.class);
    private final ComissaoQueryService comissaoQueryService = mock(ComissaoQueryService.class);
    private final DespesaQueryService despesaQueryService = mock(DespesaQueryService.class);
    private final ReceitaQueryService receitaQueryService = mock(ReceitaQueryService.class);
    private final EcommerceQueryService ecommerceQueryService = mock(EcommerceQueryService.class);
    private final ClienteQueryService clienteQueryService = mock(ClienteQueryService.class);
    private final FinanceCalculator financeCalculator = new FinanceCalculator();
    private final AgendamentoFotografoRepository agendamentoFotografoRepository = mock(AgendamentoFotografoRepository.class);

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(
            agendamentoQueryService, comissaoQueryService, despesaQueryService,
            receitaQueryService, ecommerceQueryService, clienteQueryService, financeCalculator);

        when(agendamentoQueryService.obterPorPeriodo(any(), any())).thenReturn(List.of());
        when(agendamentoQueryService.repasseRepository()).thenReturn(agendamentoFotografoRepository);
        when(agendamentoFotografoRepository.sumRepassesAtivosPorAgendamento(any())).thenReturn(List.of());
        when(comissaoQueryService.obterComissaoPorAgendamentos(anyList())).thenReturn(Map.of());
        when(despesaQueryService.obterPorPeriodo(any(), any()))
            .thenReturn(new DespesaQueryService.DespesasPorPeriodo(Map.of(), Map.of()));
        when(receitaQueryService.obterAvulsasPorPeriodo(any(), any()))
            .thenReturn(new ReceitaQueryService.ReceitasAvulsasPorMes(Map.of(), Map.of()));
    }

    @Test
    void deslocamentoRepassadoNaoEntraNaDespesa() {
        var agendamento = agendamento("575.00", "575.00", "0.00");
        agendamento.setRepassarDeslocamento(true);
        agendamento.setCustoDeslocamento(new BigDecimal("60.00"));
        when(agendamentoQueryService.obterPorPeriodo(any(), any()))
            .thenReturn(List.of(agendamento));
        when(comissaoQueryService.obterComissaoPorAgendamentos(anyList()))
            .thenReturn(Map.of(agendamento.getId(), new ComissaoQueryService.ComissaoResumo(BigDecimal.ZERO, BigDecimal.ZERO)));

        var mesAtual = mesAtual(service.calcularFinanceiroMensal(6));

        assertEquals(new BigDecimal("575.00"), mesAtual.valorConfirmados());
        assertEquals(BigDecimal.ZERO, mesAtual.despesasDeslocamento());
        assertEquals(new BigDecimal("575.00"), mesAtual.liquidoPrevisto());
        assertEquals(new BigDecimal("575.00"), mesAtual.liquidoAtual());
        assertEquals(new BigDecimal("575.00"), mesAtual.entradasRecebidas());
    }

    @Test
    void comissaoCanceladaNaoEntraNaDespesa() {
        var agendamento = agendamento("575.00", "575.00", "0.00");
        when(agendamentoQueryService.obterPorPeriodo(any(), any()))
            .thenReturn(List.of(agendamento));
        when(comissaoQueryService.obterComissaoPorAgendamentos(anyList()))
            .thenReturn(Map.of(agendamento.getId(), new ComissaoQueryService.ComissaoResumo(BigDecimal.ZERO, BigDecimal.ZERO)));

        var mesAtual = mesAtual(service.calcularFinanceiroMensal(6));

        assertEquals(BigDecimal.ZERO, mesAtual.despesasComissao());
        assertEquals(new BigDecimal("575.00"), mesAtual.liquidoPrevisto());
        assertEquals(new BigDecimal("575.00"), mesAtual.liquidoAtual());
    }

    @Test
    void comissaoPagaEntraNoRealizado() {
        var agendamento = agendamento("575.00", "575.00", "0.00");
        when(agendamentoQueryService.obterPorPeriodo(any(), any()))
            .thenReturn(List.of(agendamento));
        when(comissaoQueryService.obterComissaoPorAgendamentos(anyList()))
            .thenReturn(Map.of(agendamento.getId(), new ComissaoQueryService.ComissaoResumo(
                new BigDecimal("75.00"), new BigDecimal("75.00"))));

        var mesAtual = mesAtual(service.calcularFinanceiroMensal(6));

        assertEquals(new BigDecimal("75.00"), mesAtual.despesasComissao());
        assertEquals(new BigDecimal("500.00"), mesAtual.liquidoPrevisto());
        assertEquals(new BigDecimal("500.00"), mesAtual.liquidoAtual());
    }

    @Test
    void avulsaPrevistaEntraNoFaturamentoMasNaoNoRecebido() {
        var agendamento = agendamento("575.00", "575.00", "0.00");
        when(agendamentoQueryService.obterPorPeriodo(any(), any()))
            .thenReturn(List.of(agendamento));
        when(comissaoQueryService.obterComissaoPorAgendamentos(anyList()))
            .thenReturn(Map.of(agendamento.getId(), new ComissaoQueryService.ComissaoResumo(BigDecimal.ZERO, BigDecimal.ZERO)));

        var ym = YearMonth.now();
        when(receitaQueryService.obterAvulsasPorPeriodo(any(), any()))
            .thenReturn(new ReceitaQueryService.ReceitasAvulsasPorMes(
                Map.of(ym, new BigDecimal("100.00")), Map.of()));

        var resumo = service.calcularFinanceiroMensal(6);
        var mesAtual = mesAtual(resumo);

        assertEquals(new BigDecimal("675.00"), mesAtual.valorConfirmados());
        assertEquals(new BigDecimal("575.00"), resumo.mesAtual().valorEnsaiosConfirmados());
        assertEquals(new BigDecimal("575.00"), mesAtual.entradasRecebidas());
        assertEquals(new BigDecimal("675.00"), mesAtual.liquidoPrevisto());
        assertEquals(new BigDecimal("575.00"), mesAtual.liquidoAtual());
        assertEquals(new BigDecimal("100.00"), resumo.mesAtual().saldoRestante());
    }

    @Test
    void avulsaRecebidaEntraNoRecebido() {
        var agendamento = agendamento("575.00", "575.00", "0.00");
        when(agendamentoQueryService.obterPorPeriodo(any(), any()))
            .thenReturn(List.of(agendamento));
        when(comissaoQueryService.obterComissaoPorAgendamentos(anyList()))
            .thenReturn(Map.of(agendamento.getId(), new ComissaoQueryService.ComissaoResumo(BigDecimal.ZERO, BigDecimal.ZERO)));

        var ym = YearMonth.now();
        when(receitaQueryService.obterAvulsasPorPeriodo(any(), any()))
            .thenReturn(new ReceitaQueryService.ReceitasAvulsasPorMes(
                Map.of(), Map.of(ym, new BigDecimal("90.00"))));

        var mesAtual = mesAtual(service.calcularFinanceiroMensal(6));

        assertEquals(new BigDecimal("665.00"), mesAtual.entradasRecebidas());
        assertEquals(new BigDecimal("665.00"), mesAtual.liquidoAtual());
    }

    @Test
    void despesaPendenteReduzPrevistoMasNaoRealizado() {
        var agendamento = agendamento("575.00", "575.00", "0.00");
        when(agendamentoQueryService.obterPorPeriodo(any(), any()))
            .thenReturn(List.of(agendamento));
        when(comissaoQueryService.obterComissaoPorAgendamentos(anyList()))
            .thenReturn(Map.of(agendamento.getId(), new ComissaoQueryService.ComissaoResumo(BigDecimal.ZERO, BigDecimal.ZERO)));

        var ym = YearMonth.now();
        when(despesaQueryService.obterPorPeriodo(any(), any()))
            .thenReturn(new DespesaQueryService.DespesasPorPeriodo(
                Map.of(ym, new BigDecimal("20.00")), Map.of()));

        var mesAtual = mesAtual(service.calcularFinanceiroMensal(6));

        assertEquals(new BigDecimal("20.00"), mesAtual.despesasManuais());
        assertEquals(new BigDecimal("555.00"), mesAtual.liquidoPrevisto());
        assertEquals(new BigDecimal("575.00"), mesAtual.liquidoAtual());
    }

    private Agendamento agendamento(String valorTotalFinal, String valorEntradaPago, String valorRestante) {
        return Agendamento.builder()
            .id(UUID.randomUUID())
            .dataHoraEnsaio(LocalDateTime.now())
            .valorTotalFinal(new BigDecimal(valorTotalFinal))
            .valorEntradaPago(new BigDecimal(valorEntradaPago))
            .valorRestante(new BigDecimal(valorRestante))
            .status(StatusAgendamento.FINALIZADO)
            .build();
    }

    private DashboardMensalResponse.DadosMensais mesAtual(DashboardMensalResponse resumo) {
        var ym = YearMonth.now();
        return resumo.historico().stream()
            .filter(d -> d.mes().equals(ym.toString()))
            .findFirst()
            .orElseThrow();
    }
}
