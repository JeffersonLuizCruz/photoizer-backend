package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.despesa.service.DespesaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Transactional
public class PartilhaService {

    private final AgendamentoFotografoRepository agendamentoFotografoRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final DespesaService despesaService;

    public PartilhaService(AgendamentoFotografoRepository agendamentoFotografoRepository,
                           AgendamentoRepository agendamentoRepository,
                           DespesaService despesaService) {
        this.agendamentoFotografoRepository = agendamentoFotografoRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.despesaService = despesaService;
    }

    public void calcularPartilhaFotografo(Agendamento agendamento) {
        var links = agendamentoFotografoRepository.findByAgendamentoId(agendamento.getId());
        if (links.isEmpty()) {
            agendamento.setValorPartilhaGlobal(null);
            agendamento.setValorLucroCrm(null);
            return;
        }

        var partilhaGlobal = calcularPartilhaGlobal(agendamento);
        var somaRepasses = somarRepassesNaoCancelados(agendamento.getId());
        if (somaRepasses.compareTo(partilhaGlobal) > 0) {
            throw new IllegalArgumentException(
                "A soma dos repasses (R$ " + somaRepasses.toPlainString() + ") excede a partilha do ensaio (R$ "
                    + partilhaGlobal.toPlainString() + ")");
        }
        var lucro = partilhaGlobal.subtract(somaRepasses);

        agendamento.setValorPartilhaGlobal(partilhaGlobal);
        agendamento.setValorLucroCrm(lucro);
        agendamentoRepository.save(agendamento);
    }

    public void validarPartilha(UUID agendamentoId) {
        var agendamento = agendamentoRepository.findById(agendamentoId).orElse(null);
        if (agendamento == null) {
            return;
        }

        var partilhaGlobal = calcularPartilhaGlobal(agendamento);
        var somaRepasses = somarRepassesNaoCancelados(agendamentoId);

        if (somaRepasses.compareTo(partilhaGlobal) > 0) {
            throw new IllegalArgumentException(
                "A soma dos repasses (R$ " + somaRepasses.toPlainString() + ") excede a partilha do ensaio (R$ "
                    + partilhaGlobal.toPlainString() + ")");
        }
    }

    private BigDecimal calcularPartilhaGlobal(Agendamento agendamento) {
        var custosTotais = despesaService.somarCustosTodosFotografos(agendamento.getId());
        return agendamento.getValorTotalFinal().subtract(custosTotais);
    }

    private BigDecimal somarRepassesNaoCancelados(UUID agendamentoId) {
        return agendamentoFotografoRepository.findByAgendamentoId(agendamentoId).stream()
            .filter(l -> l.getStatus() != RepasseStatus.CANCELADO)
            .map(AgendamentoFotografo::getValorRepassar)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}