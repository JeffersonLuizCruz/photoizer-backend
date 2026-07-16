package com.photoizer.crm.documento.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class ContratoService {

    private final AgendamentoRepository agendamentoRepository;
    private final PdfGeneratorService pdfGeneratorService;

    public ContratoService(AgendamentoRepository agendamentoRepository,
                           PdfGeneratorService pdfGeneratorService) {
        this.agendamentoRepository = agendamentoRepository;
        this.pdfGeneratorService = pdfGeneratorService;
    }

    public byte[] gerarContrato(UUID agendamentoId) {
        var agendamento = agendamentoRepository.findById(agendamentoId).orElseThrow();
        agendamento.setContratoGerado(true);
        agendamentoRepository.save(agendamento);
        return pdfGeneratorService.gerarContrato(agendamento);
    }

    public byte[] gerarRecibo(UUID agendamentoId) {
        var agendamento = agendamentoRepository.findById(agendamentoId).orElseThrow();
        return pdfGeneratorService.gerarRecibo(agendamento);
    }
}
