package com.photoizer.crm.financeiro;

import com.photoizer.crm.ecommerce.model.CompraExtra;
import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import com.photoizer.crm.financeiro.model.Pagamento;
import com.photoizer.crm.financeiro.repository.PagamentoRepository;
import com.photoizer.crm.financeiro.service.FinanceiroService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReconciliarComprasExtraFinanceiroTest {

    private final CompraExtraRepository compraExtraRepository = mock(CompraExtraRepository.class);
    private final PagamentoRepository pagamentoRepository = mock(PagamentoRepository.class);
    private final FinanceiroService financeiroService = mock(FinanceiroService.class);

    private ReconciliarComprasExtraFinanceiro runner;

    @BeforeEach
    void setUp() {
        runner = new ReconciliarComprasExtraFinanceiro(
            compraExtraRepository, pagamentoRepository, financeiroService);
    }

    private CompraExtra compraPaga(UUID id, UUID agendamentoId, BigDecimal valor) {
        return CompraExtra.builder()
            .id(id)
            .agendamentoId(agendamentoId)
            .valorTotal(valor)
            .status(StatusCompraExtra.PAGA)
            .build();
    }

    @Test
    void contaCompraPagaSemRegistroFinanceiro() {
        var compraId = UUID.randomUUID();
        var agendamentoId = UUID.randomUUID();
        var compra = compraPaga(compraId, agendamentoId, new BigDecimal("120.00"));

        when(compraExtraRepository.findByStatus(StatusCompraExtra.PAGA)).thenReturn(List.of(compra));
        when(pagamentoRepository.findByCompraExtraId(compraId)).thenReturn(Optional.empty());

        runner.run();

        verify(financeiroService).registrarPagamentoExtraEcommerce(
            agendamentoId, new BigDecimal("120.00"), compraId);
    }

    @Test
    void ignoraCompraPagaJaContabilizada() {
        var compraId = UUID.randomUUID();
        var compra = compraPaga(compraId, UUID.randomUUID(), new BigDecimal("60.00"));

        when(compraExtraRepository.findByStatus(StatusCompraExtra.PAGA)).thenReturn(List.of(compra));
        when(pagamentoRepository.findByCompraExtraId(compraId))
            .thenReturn(Optional.of(Pagamento.builder().id(UUID.randomUUID()).build()));

        runner.run();

        verify(financeiroService, never()).registrarPagamentoExtraEcommerce(any(), any(), any());
    }

    @Test
    void naoProcessaComprasNaoPagas() {
        when(compraExtraRepository.findByStatus(StatusCompraExtra.PAGA)).thenReturn(List.of());
        runner.run();
        verify(financeiroService, never()).registrarPagamentoExtraEcommerce(any(), any(), any());
    }
}