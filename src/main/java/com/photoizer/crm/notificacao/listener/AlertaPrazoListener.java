package com.photoizer.crm.notificacao.listener;

import com.photoizer.crm.notificacao.service.NotificacaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AlertaPrazoListener {

    private static final Logger log = LoggerFactory.getLogger(AlertaPrazoListener.class);

    private final NotificacaoService notificacaoService;

    public AlertaPrazoListener(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }
}
