package com.photoizer.crm.financeiro;

import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import com.photoizer.crm.financeiro.service.CompraExtraFinanceiroService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Reconciliador de boot: garante que compras extras antigas ou cujo evento
 * falhou sejam contabilizadas no financeiro do agendamento.
 *
 * <p>Cada compra é processada em uma transação própria (via service), então uma
 * falha isolada não impede a correção das demais nem deixa a compra em estado
 * parcial; ela será tentada novamente no próximo boot.
 */
@Component
public class ReconciliarComprasExtraFinanceiro implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ReconciliarComprasExtraFinanceiro.class);

    private final CompraExtraRepository compraExtraRepository;
    private final CompraExtraFinanceiroService compraExtraFinanceiroService;

    public ReconciliarComprasExtraFinanceiro(CompraExtraRepository compraExtraRepository,
                                             CompraExtraFinanceiroService compraExtraFinanceiroService) {
        this.compraExtraRepository = compraExtraRepository;
        this.compraExtraFinanceiroService = compraExtraFinanceiroService;
    }

    @Override
    public void run(String... args) {
        var compras = compraExtraRepository.findAll();
        var criacoes = 0;
        var pagamentos = 0;
        var estornos = 0;

        for (var compra : compras) {
            try {
                if (compra.getStatus() == StatusCompraExtra.CANCELADA) {
                    if (Boolean.TRUE.equals(compra.getExtrasContabilizados())
                        && !Boolean.TRUE.equals(compra.getPagamentoContabilizado())) {
                        compraExtraFinanceiroService.contabilizarCancelamento(compra);
                        estornos++;
                    }
                    continue;
                }

                if (!Boolean.TRUE.equals(compra.getExtrasContabilizados())) {
                    compraExtraFinanceiroService.contabilizarCriacao(compra);
                    criacoes++;
                }
                if (compra.getStatus() == StatusCompraExtra.PAGA
                    && !Boolean.TRUE.equals(compra.getPagamentoContabilizado())) {
                    compraExtraFinanceiroService.contabilizarPagamento(compra);
                    pagamentos++;
                }
            } catch (RuntimeException ex) {
                log.atError()
                    .addKeyValue("event_action", "financeiro.reconciliacao_compra_extra_falhou")
                    .addKeyValue("compra_extra_id", compra.getId())
                    .addKeyValue("agendamento_id", compra.getAgendamentoId())
                    .setCause(ex)
                    .log("Falha ao reconciliar compra extra; será tentado no próximo boot");
            }
        }

        if (criacoes > 0 || pagamentos > 0 || estornos > 0) {
            log.info("Reconciliação financeira de compras extras: {} extra(s) contabilizado(s), "
                    + "{} pagamento(s) contabilizado(s), {} estorno(s).",
                criacoes, pagamentos, estornos);
        }
    }
}
