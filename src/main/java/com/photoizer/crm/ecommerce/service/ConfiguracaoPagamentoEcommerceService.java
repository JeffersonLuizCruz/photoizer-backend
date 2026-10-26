package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.model.ConfiguracaoPagamentoEcommerce;
import com.photoizer.crm.ecommerce.repository.ConfiguracaoPagamentoEcommerceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gerencia a configuracao global de PIX do e-commerce.
 *
 * <p>A alteracao e imediata e reflete em todas as galerias. O valor nao e
 * copiado para a compra: o checkout sempre consulta a configuracao atual.
 */
@Service
@Transactional
public class ConfiguracaoPagamentoEcommerceService {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracaoPagamentoEcommerceService.class);

    private final ConfiguracaoPagamentoEcommerceRepository repository;

    public ConfiguracaoPagamentoEcommerceService(ConfiguracaoPagamentoEcommerceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ConfiguracaoPagamentoEcommerce obter() {
        return repository.findById(ConfiguracaoPagamentoEcommerce.ID_UNICO)
            .orElseGet(ConfiguracaoPagamentoEcommerce::new);
    }

    public ConfiguracaoPagamentoEcommerce atualizar(String pixChave, String pixTipoChave) {
        var configuracao = repository.findById(ConfiguracaoPagamentoEcommerce.ID_UNICO)
            .orElseGet(() -> {
                var nova = new ConfiguracaoPagamentoEcommerce();
                nova.setId(ConfiguracaoPagamentoEcommerce.ID_UNICO);
                return nova;
            });

        var usuario = usuarioAtual();
        configuracao.setPixChave(normalizar(pixChave));
        configuracao.setPixTipoChave(normalizar(pixTipoChave));
        configuracao.setUpdatedBy(usuario);

        var salva = repository.save(configuracao);
        // Nunca logar o valor da chave: PIX/CPF/CNPJ são dados sensíveis.
        log.atInfo()
            .addKeyValue("event_action", "ecommerce.pix_configurado")
            .addKeyValue("usuario", usuario)
            .addKeyValue("tem_chave", salva.getPixChave() != null)
            .log("Configuração de PIX do e-commerce atualizada");
        return salva;
    }

    private String normalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private String usuarioAtual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "SYSTEM";
        }
        return auth.getName();
    }
}
