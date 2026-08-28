package com.photoizer.crm.financeiro;

import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import com.photoizer.crm.financeiro.repository.PagamentoRepository;
import com.photoizer.crm.financeiro.service.PagamentoService;
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
    private final PagamentoService pagamentoService;

    public ReconciliarComprasExtraFinanceiro(CompraExtraRepository compraExtraRepository,
                                             PagamentoRepository pagamentoRepository,
                                             PagamentoService pagamentoService) {
        this.compraExtraRepository = compraExtraRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.pagamentoService = pagamentoService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        var pagas = compraExtraRepository.findByStatus(StatusCompraExtra.PAGA);
        var reconciliadas = 0;
        var jaReconciliadas = 0;
        for (var compra : pagas) {
            if (pagamentoRepository.findByCompraExtraId(compra.getId()).isPresent()) {
                jaReconciliadas++;
                continue;
            }
            pagamentoService.registrarPagamentoExtraEcommerce(
                compra.getAgendamentoId(), compra.getValorTotal(), compra.getId());
            reconciliadas++;
        }
        if (reconciliadas > 0 || jaReconciliadas > 0) {
            log.info("Reconciliação financeira: {} nova(s), {} já contabilizada(s) de {} compra(s) PAGA.",
                reconciliadas, jaReconciliadas, pagas.size());
        }
    }
}