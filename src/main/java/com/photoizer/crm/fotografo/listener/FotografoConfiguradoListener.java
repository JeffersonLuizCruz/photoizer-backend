package com.photoizer.crm.fotografo.listener;

import com.photoizer.crm.auth.service.UserService;
import com.photoizer.crm.config.event.FotografoConfiguradoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * PATTERN: Event Listener (Modulith)
 *
 * Reage à configuração do nome do fotógrafo (módulo config) provisionando um
 * usuário com papel FOTOGRAFO, para que ele apareça no campo "Fotógrafo
 * responsável" da Nova Proposta. Mantém o módulo config sem dependência do
 * módulo auth/fotografo.
 *
 * Usa {@link EventListener} (síncrono) — mesmo padrão dos demais listeners do
 * projeto. O provisionamento roda em transação própria (REQUIRES_NEW) no
 * {@link UserService}, isolando falhas da gravação da configuração.
 */
@Component
public class FotografoConfiguradoListener {

    private static final Logger log = LoggerFactory.getLogger(FotografoConfiguradoListener.class);

    private final UserService userService;

    public FotografoConfiguradoListener(UserService userService) {
        this.userService = userService;
    }

    @EventListener
    public void handleFotografoConfigurado(FotografoConfiguradoEvent event) {
        try {
            userService.upsertFotografoConfigurado(event.nome());
            log.info("Usuário FOTOGRAFO provisionado/atualizado a partir da configuração: {}", event.nome());
        } catch (RuntimeException e) {
            log.warn("Falha ao provisionar usuário FOTOGRAFO a partir da configuração '{}'", event.nome(), e);
        }
    }
}
