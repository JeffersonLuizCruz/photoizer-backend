package com.photoizer.crm.documento.service;

import com.photoizer.crm.agenda.model.Agendamento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PdfGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);

    public byte[] gerarContrato(Agendamento agendamento) {
        log.info("Gerando contrato para agendamento {}", agendamento.getId());
        return new byte[0];
    }

    public byte[] gerarRecibo(Agendamento agendamento) {
        log.info("Gerando recibo para agendamento {}", agendamento.getId());
        return new byte[0];
    }
}
