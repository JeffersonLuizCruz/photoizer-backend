package com.photoizer.crm.fotografo.listener;

import com.photoizer.crm.auth.service.UserService;
import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.config.service.ConfiguracaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * PATTERN: Event Listener (Modulith) — Reconciliação no start.
 *
 * Garante que o usuário FOTOGRAFO exista sempre que houver um "Nome do
 * Fotógrafo" configurado. Cobre o caso de o valor ter sido salvo quando o
 * provisionamento automático ainda não funcionava (ou de o usuário ter sido
 * removido): no próximo start ele é criado/atualizado.
 */
@Component
public class FotografoConfiguradoReconciler {

    private static final Logger log = LoggerFactory.getLogger(FotografoConfiguradoReconciler.class);

    private final ConfiguracaoService configuracaoService;
    private final UserService userService;

    public FotografoConfiguradoReconciler(ConfiguracaoService configuracaoService, UserService userService) {
        this.configuracaoService = configuracaoService;
        this.userService = userService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconciliar() {
        var nome = configuracaoService.getValor(ConfigKey.NOME_FOTOGRAFO);
        if (nome == null || nome.isBlank()) {
            return;
        }
        try {
            userService.upsertFotografoConfigurado(nome);
            log.info("Reconciliação: usuário FOTOGRAFO garantido a partir da configuração: {}", nome.trim());
        } catch (RuntimeException e) {
            log.warn("Reconciliação: falha ao garantir usuário FOTOGRAFO a partir da configuração '{}'", nome, e);
        }
    }
}
