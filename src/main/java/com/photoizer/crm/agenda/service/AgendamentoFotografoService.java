package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.exception.FotografoNaoEncontradoException;
import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.shared.model.TipoRepasse;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.despesa.service.DespesaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AgendamentoFotografoService {

    private final AgendamentoFotografoRepository agendamentoFotografoRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final UserRepository userRepository;
    private final DespesaService despesaService;
    private final AgendamentoService agendamentoService;

    public AgendamentoFotografoService(AgendamentoFotografoRepository agendamentoFotografoRepository,
                                        AgendamentoRepository agendamentoRepository,
                                        UserRepository userRepository,
                                        DespesaService despesaService,
                                        AgendamentoService agendamentoService) {
        this.agendamentoFotografoRepository = agendamentoFotografoRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.userRepository = userRepository;
        this.despesaService = despesaService;
        this.agendamentoService = agendamentoService;
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
        var efetivo = calcularValor(agendamento, tipo, valorRepassar, percentual);

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
        validarPartilha(agendamento.getId());
        agendamentoService.calcularPartilhaFotografo(agendamento);
        return link;
    }

    public AgendamentoFotografo atualizarRepasse(UUID agendamentoId, UUID fotografoId, BigDecimal valorRepassar,
                                                 TipoRepasse tipoValor, BigDecimal percentual) {
        var link = buscarLink(agendamentoId, fotografoId);
        var tipo = tipoValor != null ? tipoValor : (link.getTipoValor() != null ? link.getTipoValor() : TipoRepasse.FIXO);
        validarPercentual(tipo, percentual);
        var agendamento = agendamentoRepository.findById(agendamentoId).orElse(null);

        if (agendamento != null) {
            var efetivo = calcularValor(agendamento, tipo, valorRepassar, percentual);
            link.setValorRepassar(efetivo);
        } else {
            link.setValorRepassar(valorRepassar != null ? valorRepassar : BigDecimal.ZERO);
        }
        link.setTipoValor(tipo);
        link.setPercentual(tipo == TipoRepasse.PERCENTUAL ? percentual : null);
        link = agendamentoFotografoRepository.save(link);

        if (agendamento != null) {
            validarPartilha(agendamentoId);
            agendamentoService.calcularPartilhaFotografo(agendamento);
        }
        return link;
    }

    public void removerFotografo(UUID agendamentoId, UUID fotografoId) {
        var link = buscarLink(agendamentoId, fotografoId);
        agendamentoFotografoRepository.delete(link);
        var agendamento = agendamentoRepository.findById(agendamentoId).orElse(null);
        if (agendamento != null) {
            agendamentoService.calcularPartilhaFotografo(agendamento);
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
        link.setStatus(RepasseStatus.PAGO);
        link.setDataPagamento(LocalDateTime.now());
        return agendamentoFotografoRepository.save(link);
    }

    public AgendamentoFotografo cancelarRepasse(UUID agendamentoId, UUID fotografoId) {
        var link = buscarLink(agendamentoId, fotografoId);
        if (link.getStatus() == RepasseStatus.PAGO) {
            throw new IllegalArgumentException("Repasse já pago não pode ser cancelado");
        }
        link.setStatus(RepasseStatus.CANCELADO);
        link.setDataPagamento(null);
        link = agendamentoFotografoRepository.save(link);
        var agendamento = agendamentoRepository.findById(agendamentoId).orElse(null);
        if (agendamento != null) {
            agendamentoService.calcularPartilhaFotografo(agendamento);
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
            link.setStatus(RepasseStatus.PAGO);
            link.setDataPagamento(LocalDateTime.now());
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

    static BigDecimal calcularValor(Agendamento agendamento, TipoRepasse tipo, BigDecimal valorRepassar, BigDecimal percentual) {
        if (tipo == TipoRepasse.PERCENTUAL) {
            if (percentual == null) throw new IllegalArgumentException("Percentual é obrigatório quando o tipo é PERCENTUAL");
            var base = agendamento.getValorTotal() != null ? agendamento.getValorTotal() : BigDecimal.ZERO;
            return base.multiply(percentual).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return valorRepassar != null ? valorRepassar : BigDecimal.ZERO;
    }

    private void validarPercentual(TipoRepasse tipo, BigDecimal percentual) {
        if (tipo == TipoRepasse.PERCENTUAL) {
            if (percentual == null) throw new IllegalArgumentException("Percentual é obrigatório para repasse percentual");
            if (percentual.signum() <= 0 || percentual.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("Percentual deve estar entre 0 e 100");
            }
        }
    }

    private void validarPartilha(UUID agendamentoId) {
        var agendamento = agendamentoRepository.findById(agendamentoId).orElse(null);
        if (agendamento == null) return;

        var custosTotais = despesaService.somarCustosTodosFotografos(agendamentoId);
        var partilhaGlobal = agendamento.getValorTotalFinal().subtract(custosTotais);
        var somaRepasses = agendamentoFotografoRepository.findByAgendamentoId(agendamentoId).stream()
            .filter(l -> l.getStatus() != RepasseStatus.CANCELADO)
            .map(AgendamentoFotografo::getValorRepassar)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (somaRepasses.compareTo(partilhaGlobal) > 0) {
            throw new IllegalArgumentException(
                "A soma dos repasses (R$ " + somaRepasses + ") excede a partilha do ensaio (R$ " + partilhaGlobal + ")");
        }
    }
}
