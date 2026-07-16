package com.photoizer.crm.financeiro.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.financeiro.model.FotoExtra;
import com.photoizer.crm.financeiro.model.Pagamento;
import com.photoizer.crm.financeiro.repository.FotoExtraRepository;
import com.photoizer.crm.financeiro.repository.PagamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class FinanceiroService {

    private final PagamentoRepository pagamentoRepository;
    private final FotoExtraRepository fotoExtraRepository;
    private final AgendamentoRepository agendamentoRepository;

    public FinanceiroService(PagamentoRepository pagamentoRepository,
                             FotoExtraRepository fotoExtraRepository,
                             AgendamentoRepository agendamentoRepository) {
        this.pagamentoRepository = pagamentoRepository;
        this.fotoExtraRepository = fotoExtraRepository;
        this.agendamentoRepository = agendamentoRepository;
    }

    public Pagamento registrarPagamento(UUID agendamentoId, Pagamento pagamento) {
        var agendamento = agendamentoRepository.findById(agendamentoId).orElseThrow();
        pagamento.setAgendamento(agendamento);

        var novoValorPago = agendamento.getValorEntradaPago().add(pagamento.getValor());
        agendamento.setValorEntradaPago(novoValorPago);
        agendamento.setValorRestante(agendamento.getValorTotalFinal().subtract(novoValorPago));

        if (agendamento.getValorRestante().compareTo(BigDecimal.ZERO) <= 0) {
            agendamento.setStatus(StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL);
        }

        agendamentoRepository.save(agendamento);
        return pagamentoRepository.save(pagamento);
    }

    public FotoExtra adicionarFotoExtra(UUID agendamentoId, int quantidade, BigDecimal valorUnitario) {
        var agendamento = agendamentoRepository.findById(agendamentoId).orElseThrow();

        var valorTotal = valorUnitario.multiply(BigDecimal.valueOf(quantidade));
        var fotoExtra = FotoExtra.builder()
            .agendamento(agendamento)
            .quantidade(quantidade)
            .valorUnitario(valorUnitario)
            .valorTotal(valorTotal)
            .build();

        agendamento.setValorExtras(agendamento.getValorExtras().add(valorTotal));
        agendamento.setValorTotalFinal(agendamento.getValorTotal().add(agendamento.getValorExtras()));
        agendamentoRepository.save(agendamento);

        return fotoExtraRepository.save(fotoExtra);
    }

    @Transactional(readOnly = true)
    public List<Pagamento> listarPagamentos(UUID agendamentoId) {
        return pagamentoRepository.findAll();
    }

    public boolean isClienteBloqueado(UUID clienteId) {
        var agendamentos = agendamentoRepository.findAll();
        return agendamentos.stream()
            .filter(a -> a.getCliente().getId().equals(clienteId))
            .anyMatch(a -> a.getValorRestante().compareTo(BigDecimal.ZERO) > 0
                && a.getStatus() != StatusAgendamento.CANCELADO);
    }
}
