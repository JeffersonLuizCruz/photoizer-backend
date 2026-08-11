package com.photoizer.crm.financeiro;

import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import com.photoizer.crm.financeiro.repository.PagamentoRepository;
import com.photoizer.crm.financeiro.service.FinanceiroService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReconciliarComprasExtraFinanceiro implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ReconciliarComprasExtraFinanceiro.class);

    private final CompraExtraRepository compraExtraRepository;
    private final PagamentoRepository pagamentoRepository;
    private final FinanceiroService financeiroService;

    public ReconciliarComprasExtraFinanceiro(CompraExtraRepository compraExtraRepository,
                                             PagamentoRepository pagamentoRepository,
                                             FinanceiroService financeiroService) {
        this.compraExtraRepository = compraExtraRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.financeiroService = financeiroService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        var pagas = compraExtraRepository.findByStatus(StatusCompraExtra.PAGA);
        var reconciliadas = 0;
        for (var compra : pagas) {
            if (pagamentoRepository.findByCompraExtraId(compra.getId()).isPresent()) {
                continue;
            }
            financeiroService.registrarPagamentoExtraEcommerce(
                compra.getAgendamentoId(), compra.getValorTotal(), compra.getId());
            reconciliadas++;
        }
        if (reconciliadas > 0) {
            log.info("Reconciliação financeira: {} compra(s) extra(s) PAGA contabilizada(s) no agendamento.", reconciliadas);
        }
    }
}