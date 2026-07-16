package com.photoizer.crm.notificacao.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoService.class);

    public void enviarLembrete(String destinatario, String mensagem) {
        log.info("Enviando lembrete para {}: {}", destinatario, mensagem);
    }

    public void enviarAlerta(String destinatario, String mensagem) {
        log.info("Enviando alerta para {}: {}", destinatario, mensagem);
    }
}
