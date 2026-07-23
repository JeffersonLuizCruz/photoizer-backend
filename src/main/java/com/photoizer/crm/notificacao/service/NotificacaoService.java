package com.photoizer.crm.notificacao.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class NotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoService.class);

    public void enviarLembrete(String destinatario, String mensagem) {
        log.info("Enviando lembrete para {}: {}", destinatario, mensagem);
    }

    public void enviarAlerta(String destinatario, String mensagem) {
        log.info("Enviando alerta para {}: {}", destinatario, mensagem);
    }

    public void notificarNovaCompraExtra(UUID agendamentoId, UUID compraExtraId, BigDecimal valorTotal, int quantidade) {
        log.info("[NOTIFICACAO ADMIN] Nova compra de extras! Agendamento: {}, Valor: R$ {}, Fotos: {}",
            agendamentoId, valorTotal, quantidade);
    }

    public void notificarCompraExtraConfirmada(UUID agendamentoId, UUID compraExtraId, BigDecimal valorTotal) {
        log.info("[NOTIFICACAO ADMIN] Compra de extras confirmada! Agendamento: {}, Valor: R$ {}",
            agendamentoId, valorTotal);
    }
}
