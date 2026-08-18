package com.photoizer.crm.financeiro.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.comissao.repository.IndicacaoRepository;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.despesa.repository.DespesaRepository;
import com.photoizer.crm.financeiro.model.Pagamento;
import com.photoizer.crm.financeiro.repository.FotoExtraRepository;
import com.photoizer.crm.financeiro.repository.PagamentoRepository;
import com.photoizer.crm.financeiro.repository.ReceitaRepository;
import com.photoizer.crm.financeiro.repository.VideoExtraRepository;
import com.photoizer.crm.indicador.service.IndicadorService;
import com.photoizer.crm.pacote.repository.PacoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceiroServiceTest {

    private final PagamentoRepository pagamentoRepository = mock(PagamentoRepository.class);
    private final FotoExtraRepository fotoExtraRepository = mock(FotoExtraRepository.class);
    private final VideoExtraRepository videoExtraRepository = mock(VideoExtraRepository.class);
    private final AgendamentoRepository agendamentoRepository = mock(AgendamentoRepository.class);
    private final PacoteRepository pacoteRepository = mock(PacoteRepository.class);
    private final IndicacaoRepository indicacaoRepository = mock(IndicacaoRepository.class);
    private final IndicadorService indicadorService = mock(IndicadorService.class);
    private final ConfiguracaoService configuracaoService = mock(ConfiguracaoService.class);
    private final DespesaRepository despesaRepository = mock(DespesaRepository.class);
    private final ReceitaRepository receitaRepository = mock(ReceitaRepository.class);
    private final AgendamentoFotografoRepository agendamentoFotografoRepository = mock(AgendamentoFotografoRepository.class);

    private FinanceiroService service;

    @BeforeEach
    void setUp() {
        service = new FinanceiroService(
            pagamentoRepository, fotoExtraRepository, videoExtraRepository,
            agendamentoRepository, pacoteRepository, indicacaoRepository,
            indicadorService, configuracaoService, despesaRepository, receitaRepository,
            agendamentoFotografoRepository);
    }

    private Agendamento agendamentoCom(StatusAgendamento status) {
        return Agendamento.builder()
            .id(UUID.randomUUID())
            .valorTotal(new BigDecimal("1000.00"))
            .valorExtras(BigDecimal.ZERO)
            .valorTotalFinal(new BigDecimal("1000.00"))
            .valorEntradaPago(new BigDecimal("300.00"))
            .valorRestante(new BigDecimal("700.00"))
            .status(status)
            .build();
    }

    @Test
    void registrarPagamentoExtraEcommerce_atualizaCamposECriaPagamento() {
        var agendamento = agendamentoCom(StatusAgendamento.REALIZADO);
        when(agendamentoRepository.findById(agendamento.getId()))
            .thenReturn(Optional.of(agendamento));
        when(pagamentoRepository.save(any(Pagamento.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        var compar = ArgumentCaptor.forClass(Agendamento.class);
        service.registrarPagamentoExtraEcommerce(
            agendamento.getId(), new BigDecimal("150.00"), UUID.randomUUID());

        verify(agendamentoRepository).save(compar.capture());
        var salvo = compar.getValue();
        assertEquals(new BigDecimal("150.00"), salvo.getValorExtras());
        assertEquals(new BigDecimal("1150.00"), salvo.getValorTotalFinal());
        assertEquals(new BigDecimal("450.00"), salvo.getValorEntradaPago());
        assertEquals(new BigDecimal("700.00"), salvo.getValorRestante());

        var pagamentoCap = ArgumentCaptor.forClass(Pagamento.class);
        verify(pagamentoRepository).save(pagamentoCap.capture());
        var pagamento = pagamentoCap.getValue();
        assertNotNull(pagamento.getCompraExtraId());
        assertEquals(new BigDecimal("150.00"), pagamento.getValor());
    }

    @Test
    void registrarPagamentoExtraEcommerce_naoAlteraStatus() {
        var agendamento = agendamentoCom(StatusAgendamento.EM_EDICAO);
        when(agendamentoRepository.findById(agendamento.getId()))
            .thenReturn(Optional.of(agendamento));
        when(pagamentoRepository.save(any(Pagamento.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.registrarPagamentoExtraEcommerce(
            agendamento.getId(), new BigDecimal("50.00"), UUID.randomUUID());

        var compar = ArgumentCaptor.forClass(Agendamento.class);
        verify(agendamentoRepository).save(compar.capture());
        assertEquals(StatusAgendamento.EM_EDICAO, compar.getValue().getStatus());
    }

    @Test
    void registrarPagamentoExtraEcommerce_rejeitaAgendamentoInexistente() {
        var id = UUID.randomUUID();
        when(agendamentoRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
            () -> service.registrarPagamentoExtraEcommerce(id, new BigDecimal("10.00"), UUID.randomUUID()));

        verify(pagamentoRepository, never()).save(any(Pagamento.class));
    }
}