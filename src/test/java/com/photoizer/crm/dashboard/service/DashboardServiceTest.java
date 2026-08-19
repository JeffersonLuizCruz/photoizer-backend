package com.photoizer.crm.dashboard.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.cliente.repository.ClienteRepository;
import com.photoizer.crm.comissao.model.Indicacao;
import com.photoizer.crm.comissao.repository.IndicacaoRepository;
import com.photoizer.crm.dashboard.api.DashboardMensalResponse;
import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.despesa.model.StatusDespesa;
import com.photoizer.crm.despesa.repository.DespesaRepository;
import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import com.photoizer.crm.financeiro.model.Receita;
import com.photoizer.crm.financeiro.model.StatusReceita;
import com.photoizer.crm.financeiro.model.TipoServico;
import com.photoizer.crm.financeiro.repository.ReceitaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private final AgendamentoRepository agendamentoRepository = mock(AgendamentoRepository.class);
    private final AgendamentoFotografoRepository agendamentoFotografoRepository = mock(AgendamentoFotografoRepository.class);
    private final IndicacaoRepository indicacaoRepository = mock(IndicacaoRepository.class);
    private final DespesaRepository despesaRepository = mock(DespesaRepository.class);
    private final CompraExtraRepository compraExtraRepository = mock(CompraExtraRepository.class);
    private final ClienteRepository clienteRepository = mock(ClienteRepository.class);
    private final ReceitaRepository receitaRepository = mock(ReceitaRepository.class);

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(
            agendamentoRepository, agendamentoFotografoRepository, indicacaoRepository, despesaRepository,
            compraExtraRepository, clienteRepository, receitaRepository);
        when(agendamentoRepository.findByDataBetween(any(), any(), anyList())).thenReturn(List.of());
        when(indicacaoRepository.findByAgendamentoIdIn(anyList())).thenReturn(List.of());
        when(despesaRepository.findByDataBetweenOrderByDataDesc(any(), any())).thenReturn(List.of());
        when(receitaRepository.findAll()).thenReturn(List.of());
        when(compraExtraRepository.findByStatus(any())).thenReturn(List.of());
    }

    @Test
    void deslocamentoRepassadoNaoEntraNaDespesa() {
        var agendamento = agendamento("575.00", "575.00", "0.00");
        agendamento.setRepassarDeslocamento(true);
        agendamento.setCustoDeslocamento(new BigDecimal("60.00"));
        when(agendamentoRepository.findByDataBetween(any(), any(), anyList()))
            .thenReturn(List.of(agendamento));

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
        var indicacao = Indicacao.builder()
            .agendamentoId(agendamento.getId())
            .valorComissao(new BigDecimal("75.00"))
            .status("CANCELADA")
            .build();
        when(agendamentoRepository.findByDataBetween(any(), any(), anyList()))
            .thenReturn(List.of(agendamento));
        when(indicacaoRepository.findByAgendamentoIdIn(anyList())).thenReturn(List.of(indicacao));

        var mesAtual = mesAtual(service.calcularFinanceiroMensal(6));

        assertEquals(BigDecimal.ZERO, mesAtual.despesasComissao());
        assertEquals(new BigDecimal("575.00"), mesAtual.liquidoPrevisto());
        assertEquals(new BigDecimal("575.00"), mesAtual.liquidoAtual());
    }

    @Test
    void comissaoPagaEntraNoRealizado() {
        var agendamento = agendamento("575.00", "575.00", "0.00");
        var indicacao = Indicacao.builder()
            .agendamentoId(agendamento.getId())
            .valorComissao(new BigDecimal("75.00"))
            .status("PAGA")
            .build();
        when(agendamentoRepository.findByDataBetween(any(), any(), anyList()))
            .thenReturn(List.of(agendamento));
        when(indicacaoRepository.findByAgendamentoIdIn(anyList())).thenReturn(List.of(indicacao));

        var mesAtual = mesAtual(service.calcularFinanceiroMensal(6));

        assertEquals(new BigDecimal("75.00"), mesAtual.despesasComissao());
        assertEquals(new BigDecimal("500.00"), mesAtual.liquidoPrevisto());
        assertEquals(new BigDecimal("500.00"), mesAtual.liquidoAtual());
    }

    @Test
    void avulsaPrevistaEntraNoFaturamentoMasNaoNoRecebido() {
        var agendamento = agendamento("575.00", "575.00", "0.00");
        var avulsa = Receita.builder()
            .agendamentoId(null)
            .valorBruto(new BigDecimal("100.00"))
            .valorFinal(new BigDecimal("90.00"))
            .valorRecebido(BigDecimal.ZERO)
            .status(StatusReceita.PENDENTE)
            .dataPrevisaoRecebimento(LocalDate.now())
            .tipoServico(TipoServico.ENSAIO)
            .clienteNome("Cliente")
            .build();
        var avulsaCancelada = Receita.builder()
            .agendamentoId(null)
            .valorBruto(new BigDecimal("200.00"))
            .valorFinal(new BigDecimal("180.00"))
            .valorRecebido(BigDecimal.ZERO)
            .status(StatusReceita.CANCELADO)
            .dataPrevisaoRecebimento(LocalDate.now())
            .tipoServico(TipoServico.ENSAIO)
            .clienteNome("Cliente")
            .build();
        when(agendamentoRepository.findByDataBetween(any(), any(), anyList()))
            .thenReturn(List.of(agendamento));
        when(receitaRepository.findAll()).thenReturn(List.of(avulsa, avulsaCancelada));

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
        var avulsa = Receita.builder()
            .agendamentoId(null)
            .valorBruto(new BigDecimal("100.00"))
            .valorFinal(new BigDecimal("90.00"))
            .valorRecebido(new BigDecimal("90.00"))
            .status(StatusReceita.PAGO_PARCIAL)
            .dataRecebimentoReal(LocalDateTime.now())
            .tipoServico(TipoServico.ENSAIO)
            .clienteNome("Cliente")
            .build();
        when(agendamentoRepository.findByDataBetween(any(), any(), anyList()))
            .thenReturn(List.of(agendamento));
        when(receitaRepository.findAll()).thenReturn(List.of(avulsa));

        var mesAtual = mesAtual(service.calcularFinanceiroMensal(6));

        assertEquals(new BigDecimal("665.00"), mesAtual.entradasRecebidas());
        assertEquals(new BigDecimal("665.00"), mesAtual.liquidoAtual());
    }

    @Test
    void despesaPendenteReduzPrevistoMasNaoRealizado() {
        var agendamento = agendamento("575.00", "575.00", "0.00");
        var despesa = Despesa.builder()
            .descricao("Despesa")
            .valor(new BigDecimal("20.00"))
            .status(StatusDespesa.PENDENTE)
            .data(LocalDate.now())
            .build();
        when(agendamentoRepository.findByDataBetween(any(), any(), anyList()))
            .thenReturn(List.of(agendamento));
        when(despesaRepository.findByDataBetweenOrderByDataDesc(any(), any())).thenReturn(List.of(despesa));

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