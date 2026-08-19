package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.exception.FotografoNaoEncontradoException;
import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.shared.model.TipoRepasse;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AgendamentoFotografoService {

    private final AgendamentoFotografoRepository agendamentoFotografoRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final UserRepository userRepository;
    private final PartilhaService partilhaService;
    private final AgendamentoValoresCalculator agendamentoValoresCalculator;

    public AgendamentoFotografoService(AgendamentoFotografoRepository agendamentoFotografoRepository,
                                       AgendamentoRepository agendamentoRepository,
                                       UserRepository userRepository,
                                       PartilhaService partilhaService,
                                       AgendamentoValoresCalculator agendamentoValoresCalculator) {
        this.agendamentoFotografoRepository = agendamentoFotografoRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.userRepository = userRepository;
        this.partilhaService = partilhaService;
        this.agendamentoValoresCalculator = agendamentoValoresCalculator;
    }

    @Transactional(readOnly = true)
    public List<AgendamentoFotografo> listarFotografos(UUID agendamentoId) {
        return agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(agendamentoId);
    }

    public AgendamentoFotografo adicionarFotografo(UUID agendamentoId, UUID fotografoId, BigDecimal valorRepassar,
                                                   TipoRepasse tipoValor, BigDecimal percentual) {
        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado: " + agendamentoId));
        var fotografo = userRepository.findById(fotografoId)
            .orElseThrow(() -> new FotografoNaoEncontradoException(fotografoId));

        var existente = agendamentoFotografoRepository.findByAgendamentoId(agendamentoId).stream()
            .filter(af -> af.getFotografo().getId().equals(fotografoId))
            .findFirst();
        if (existente.isPresent()) {
            throw new IllegalArgumentException("Parceiro já vinculado a este agendamento");
        }

        var tipo = tipoValor != null ? tipoValor : TipoRepasse.FIXO;
        validarPercentual(tipo, percentual);
        var efetivo = agendamentoValoresCalculator.calcularValorRepasse(
            agendamento.getValorTotal(), tipo, valorRepassar, percentual);

        var link = AgendamentoFotografo.builder()
            .agendamento(agendamento)
            .fotografo(fotografo)
            .tipoValor(tipo)
            .percentual(tipo == TipoRepasse.PERCENTUAL ? percentual : null)
            .papelParceiro(fotografo.getPapel())
            .valorRepassar(efetivo)
            .status(RepasseStatus.PENDENTE)
            .build();
        link = agendamentoFotografoRepository.save(link);
        partilhaService.validarPartilha(agendamento.getId());
        partilhaService.calcularPartilhaFotografo(agendamento);
        return link;
    }

    public AgendamentoFotografo atualizarRepasse(UUID agendamentoId, UUID fotografoId, BigDecimal valorRepassar,
                                                 TipoRepasse tipoValor, BigDecimal percentual) {
        var link = buscarLink(agendamentoId, fotografoId);
        var tipo = tipoValor != null ? tipoValor : (link.getTipoValor() != null ? link.getTipoValor() : TipoRepasse.FIXO);
        validarPercentual(tipo, percentual);
        var agendamento = agendamentoRepository.findById(agendamentoId).orElse(null);

        if (agendamento != null) {
            var efetivo = agendamentoValoresCalculator.calcularValorRepasse(
                agendamento.getValorTotal(), tipo, valorRepassar, percentual);
            link.atualizarRepasse(tipo, tipo == TipoRepasse.PERCENTUAL ? percentual : null, efetivo);
        } else {
            link.atualizarRepasse(tipo, tipo == TipoRepasse.PERCENTUAL ? percentual : null,
                valorRepassar != null ? valorRepassar : BigDecimal.ZERO);
        }
        link = agendamentoFotografoRepository.save(link);

        if (agendamento != null) {
            partilhaService.validarPartilha(agendamentoId);
            partilhaService.calcularPartilhaFotografo(agendamento);
        }
        return link;
    }

    public void removerFotografo(UUID agendamentoId, UUID fotografoId) {
        var link = buscarLink(agendamentoId, fotografoId);
        agendamentoFotografoRepository.delete(link);
        var agendamento = agendamentoRepository.findById(agendamentoId).orElse(null);
        if (agendamento != null) {
            partilhaService.calcularPartilhaFotografo(agendamento);
        }
    }

    public AgendamentoFotografo pagarRepasse(UUID agendamentoId, UUID fotografoId) {
        var link = buscarLink(agendamentoId, fotografoId);
        if (link.getStatus() == RepasseStatus.PAGO) {
            throw new IllegalArgumentException("Este repasse já foi pago");
        }
        if (link.getStatus() == RepasseStatus.CANCELADO) {
            throw new IllegalArgumentException("Repasse cancelado não pode ser pago");
        }
        link.pagar(LocalDateTime.now());
        return agendamentoFotografoRepository.save(link);
    }

    public AgendamentoFotografo cancelarRepasse(UUID agendamentoId, UUID fotografoId) {
        var link = buscarLink(agendamentoId, fotografoId);
        if (link.getStatus() == RepasseStatus.PAGO) {
            throw new IllegalArgumentException("Repasse já pago não pode ser cancelado");
        }
        link.cancelar();
        link = agendamentoFotografoRepository.save(link);
        var agendamento = agendamentoRepository.findById(agendamentoId).orElse(null);
        if (agendamento != null) {
            partilhaService.calcularPartilhaFotografo(agendamento);
        }
        return link;
    }

    public List<AgendamentoFotografo> pagarRepasseLote(List<UUID> ids) {
        var links = agendamentoFotografoRepository.findAllById(ids);
        for (var link : links) {
            if (link.getStatus() == RepasseStatus.CANCELADO) {
                throw new IllegalArgumentException("Repasse cancelado não pode ser pago: " + link.getId());
            }
            if (link.getStatus() == RepasseStatus.PAGO) continue;
            link.pagar(LocalDateTime.now());
        }
        return agendamentoFotografoRepository.saveAll(links);
    }

    @Transactional(readOnly = true)
    public List<AgendamentoFotografo> listarPendentes() {
        return agendamentoFotografoRepository.findByStatusWithAgendamento(RepasseStatus.PENDENTE)
            .stream().filter(l -> l.getStatus() == RepasseStatus.PENDENTE).toList();
    }

    @Transactional(readOnly = true)
    public List<AgendamentoFotografo> listarPendentesPorFotografo(UUID fotografoId) {
        return agendamentoFotografoRepository.findByFotografoIdAndStatusWithAgendamento(fotografoId, RepasseStatus.PENDENTE);
    }

    private AgendamentoFotografo buscarLink(UUID agendamentoId, UUID fotografoId) {
        return agendamentoFotografoRepository.findByAgendamentoId(agendamentoId).stream()
            .filter(af -> af.getFotografo().getId().equals(fotografoId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Parceiro " + fotografoId + " não está vinculado ao agendamento " + agendamentoId));
    }

    private void validarPercentual(TipoRepasse tipo, BigDecimal percentual) {
        if (tipo == TipoRepasse.PERCENTUAL) {
            if (percentual == null) throw new IllegalArgumentException("Percentual é obrigatório para repasse percentual");
            if (percentual.signum() <= 0 || percentual.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("Percentual deve estar entre 0 e 100");
            }
        }
    }
}