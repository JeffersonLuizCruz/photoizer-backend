package com.photoizer.crm.financeiro.service;

import com.photoizer.crm.financeiro.event.ExtrasAdicionadosEvent;
import com.photoizer.crm.financeiro.exception.AgendamentoNaoEncontradoParaFinanceiroException;
import com.photoizer.crm.financeiro.exception.IndicadorInvalidoException;
import com.photoizer.crm.financeiro.exception.OperacaoNaoPermitidaException;
import com.photoizer.crm.financeiro.exception.ValorInvalidoException;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.comissao.event.ComissaoSolicitadaEvent;
import com.photoizer.crm.comissao.model.OrigemIndicacao;
import com.photoizer.crm.financeiro.model.ExtraServico;
import com.photoizer.crm.financeiro.model.TipoExtra;
import com.photoizer.crm.financeiro.repository.ExtraServicoRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service responsável pela venda de fotos e vídeos extras.
 *
 * Pattern: SRP — extraído de FinanceiroService.
 * Publica eventos de domínio (ExtrasAdicionadosEvent + ComissaoSolicitadaEvent)
 * em vez de mutar diretamente o Agendamento e criar Indicacao.
 */
@Service
@Transactional
public class ExtraVendaService {

    private final ExtraServicoRepository extraServicoRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ExtraVendaService(ExtraServicoRepository extraServicoRepository,
                             AgendamentoRepository agendamentoRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.extraServicoRepository = extraServicoRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Adiciona fotos extras a um agendamento.
     * Publica ExtrasAdicionadosEvent (agenda atualiza valorExtras/valorTotalFinal)
     * e ComissaoSolicitadaEvent (comissao cria Indicacao).
     */
    public ExtraServico adicionarFotoExtra(UUID agendamentoId, int quantidade, BigDecimal valorUnitario,
                                           String indicadorNome, String indicadorTelefone, UUID indicadorId) {
        if (quantidade <= 0) {
            throw new ValorInvalidoException("Quantidade deve ser maior que zero");
        }
        if (valorUnitario == null || valorUnitario.signum() <= 0) {
            throw new ValorInvalidoException("Valor unitário deve ser maior que zero");
        }

        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new AgendamentoNaoEncontradoParaFinanceiroException(agendamentoId));

        if (agendamento.getStatus() == StatusAgendamento.CANCELADO
            || agendamento.getStatus() == StatusAgendamento.NO_SHOW
            || agendamento.getStatus() == StatusAgendamento.FINALIZADO) {
            throw new OperacaoNaoPermitidaException(
                "Não é possível adicionar extras para agendamento " + agendamento.getStatus());
        }

        var valorTotal = valorUnitario.multiply(BigDecimal.valueOf(quantidade));
        var extra = ExtraServico.builder()
            .agendamento(agendamento)
            .tipo(TipoExtra.FOTO)
            .quantidade(quantidade)
            .valorUnitario(valorUnitario)
            .valorTotal(valorTotal)
            .build();
        var saved = extraServicoRepository.save(extra);

        eventPublisher.publishEvent(new ExtrasAdicionadosEvent(
            agendamentoId, "FOTO", quantidade, valorUnitario, valorTotal));

        if (indicadorId != null) {
            if (indicadorNome == null || indicadorNome.isBlank()
                || indicadorTelefone == null || indicadorTelefone.isBlank()) {
                throw new IndicadorInvalidoException(
                    "indicadorNome e indicadorTelefone são obrigatórios quando indicadorId é informado");
            }
            eventPublisher.publishEvent(new ComissaoSolicitadaEvent(
                agendamentoId, indicadorId, indicadorNome, indicadorTelefone,
                OrigemIndicacao.FOTO_EXTRA, valorTotal));
        }

        return saved;
    }

    /**
     * Adiciona vídeos extras a um agendamento.
     * Publica ExtrasAdicionadosEvent (agenda atualiza valorExtras/valorTotalFinal)
     * e ComissaoSolicitadaEvent (comissao cria Indicacao).
     */
    public ExtraServico adicionarVideoExtra(UUID agendamentoId, int quantidade, BigDecimal valorUnitario,
                                            String indicadorNome, String indicadorTelefone, UUID indicadorId) {
        if (quantidade <= 0) {
            throw new ValorInvalidoException("Quantidade deve ser maior que zero");
        }
        if (valorUnitario == null || valorUnitario.signum() <= 0) {
            throw new ValorInvalidoException("Valor unitário deve ser maior que zero");
        }

        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new AgendamentoNaoEncontradoParaFinanceiroException(agendamentoId));

        if (agendamento.getStatus() == StatusAgendamento.CANCELADO
            || agendamento.getStatus() == StatusAgendamento.NO_SHOW
            || agendamento.getStatus() == StatusAgendamento.FINALIZADO) {
            throw new OperacaoNaoPermitidaException(
                "Não é possível adicionar extras para agendamento " + agendamento.getStatus());
        }

        var valorTotal = valorUnitario.multiply(BigDecimal.valueOf(quantidade));
        var extra = ExtraServico.builder()
            .agendamento(agendamento)
            .tipo(TipoExtra.VIDEO)
            .quantidade(quantidade)
            .valorUnitario(valorUnitario)
            .valorTotal(valorTotal)
            .build();
        var saved = extraServicoRepository.save(extra);

        eventPublisher.publishEvent(new ExtrasAdicionadosEvent(
            agendamentoId, "VIDEO", quantidade, valorUnitario, valorTotal));

        if (indicadorId != null) {
            if (indicadorNome == null || indicadorNome.isBlank()
                || indicadorTelefone == null || indicadorTelefone.isBlank()) {
                throw new IndicadorInvalidoException(
                    "indicadorNome e indicadorTelefone são obrigatórios quando indicadorId é informado");
            }
            eventPublisher.publishEvent(new ComissaoSolicitadaEvent(
                agendamentoId, indicadorId, indicadorNome, indicadorTelefone,
                OrigemIndicacao.VIDEO_EXTRA, valorTotal));
        }

        return saved;
    }
}
