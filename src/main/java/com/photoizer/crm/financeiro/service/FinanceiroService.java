package com.photoizer.crm.financeiro.service;

import com.photoizer.crm.financeiro.api.PagamentoRequest;
import com.photoizer.crm.financeiro.model.ExtraServico;
import com.photoizer.crm.financeiro.model.Pagamento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrator fino que delega para services especializados.
 *
 * Pattern: Facade — mantém o contrato da API existente (controllers já dependem deste service)
 * enquanto delega a lógica real para PagamentoService, ExtraVendaService e FinanceiroQueryService.
 *-isso permite refatoração incremental sem quebrar o frontend.
 *
 * NOTA: Este service será gradualmente eliminado à medida que os controllers
 * passem a injetar diretamente os services especializados.
 */
@Service
@Transactional
public class FinanceiroService {

    private final PagamentoService pagamentoService;
    private final ExtraVendaService extraVendaService;
    private final FinanceiroQueryService queryService;

    public FinanceiroService(PagamentoService pagamentoService,
                             ExtraVendaService extraVendaService,
                             FinanceiroQueryService queryService) {
        this.pagamentoService = pagamentoService;
        this.extraVendaService = extraVendaService;
        this.queryService = queryService;
    }

    @Transactional(readOnly = true)
    public com.photoizer.crm.financeiro.api.FinanceiroPreviewResponse calcularPreview(UUID pacoteId, BigDecimal taxaDeslocamento) {
        return queryService.calcularPreview(pacoteId, taxaDeslocamento);
    }

    @Transactional(readOnly = true)
    public com.photoizer.crm.financeiro.api.FinanceiroResumoResponse calcularResumo(
            java.time.LocalDateTime dataInicio, java.time.LocalDateTime dataFim) {
        return queryService.calcularResumo(dataInicio, dataFim);
    }

    @Transactional(readOnly = true)
    public com.photoizer.crm.financeiro.api.FinanceiroRelatoriosResponse calcularRelatorios(
            java.time.LocalDateTime dataInicio, java.time.LocalDateTime dataFim) {
        return queryService.calcularRelatorios(dataInicio, dataFim);
    }

    @Transactional(readOnly = true)
    public com.photoizer.crm.financeiro.api.FinanceiroTrabalhoResponse resumoPorAgendamento(UUID agendamentoId) {
        return queryService.resumoPorAgendamento(agendamentoId);
    }

    @Transactional(readOnly = true)
    public com.photoizer.crm.financeiro.api.FluxoCaixaResponse calcularFluxoCaixa(
            java.time.LocalDate inicio, java.time.LocalDate fim, String visao) {
        return queryService.calcularFluxoCaixa(inicio, fim, visao);
    }

    public Pagamento registrarPagamento(UUID agendamentoId, PagamentoRequest request) {
        return pagamentoService.registrarPagamento(agendamentoId, request);
    }

    public void registrarPagamentoExtraEcommerce(UUID agendamentoId, BigDecimal valor, UUID compraExtraId) {
        pagamentoService.registrarPagamentoExtraEcommerce(agendamentoId, valor, compraExtraId);
    }

    public ExtraServico adicionarFotoExtra(UUID agendamentoId, int quantidade, BigDecimal valorUnitario,
                                           String indicadorNome, String indicadorTelefone, UUID indicadorId) {
        return extraVendaService.adicionarFotoExtra(agendamentoId, quantidade, valorUnitario,
            indicadorNome, indicadorTelefone, indicadorId);
    }

    public ExtraServico adicionarVideoExtra(UUID agendamentoId, int quantidade, BigDecimal valorUnitario,
                                            String indicadorNome, String indicadorTelefone, UUID indicadorId) {
        return extraVendaService.adicionarVideoExtra(agendamentoId, quantidade, valorUnitario,
            indicadorNome, indicadorTelefone, indicadorId);
    }

    @Transactional(readOnly = true)
    public List<com.photoizer.crm.financeiro.api.PagamentoResponse> listarPagamentos(UUID agendamentoId) {
        return pagamentoService.listarPagamentos(agendamentoId);
    }

    @Transactional(readOnly = true)
    public boolean isClienteBloqueado(UUID clienteId) {
        return queryService.isClienteBloqueado(clienteId);
    }
}
