package com.photoizer.crm.config.seed;

import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.config.model.Configuracao;
import com.photoizer.crm.config.repository.ConfiguracaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PATTERN: Composite Seeder (CommandLineRunner + @Order)
 *
 * Seeder responsável por criar as configurações base do sistema.
 * Cada módulo é dono dos seus dados semeados — responsabilidade única.
 *
 * Idempotência: verifica configuracaoRepository.count() == 0 antes de inserir.
 * @Order(10) — roda após AuthUserSeeder (sem dependência funcional, mas ordenado para debug).
 */
@Component
@Order(10)
public class ConfigBaseSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigBaseSeeder.class);

    private final ConfiguracaoRepository configuracaoRepository;

    public ConfigBaseSeeder(ConfiguracaoRepository configuracaoRepository) {
        this.configuracaoRepository = configuracaoRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (configuracaoRepository.count() == 0) {
            var configs = List.of(
                createConfig(ConfigKey.VALOR_FOTO_EXTRA),
                createConfig(ConfigKey.VALOR_VIDEO_EXTRA),
                createConfig(ConfigKey.PERCENTUAL_COMISSAO),
                createConfig(ConfigKey.PERCENTUAL_ENTRADA),
                createConfig(ConfigKey.TAXA_DESLOCAMENTO)
            );
            configuracaoRepository.saveAll(configs);
            log.info("Configurações base semeadas: {}", configs.size());
        }
    }

    private Configuracao createConfig(ConfigKey key) {
        var config = new Configuracao();
        config.setChave(key.getKey());
        config.setValor(key.getDefaultValue());
        return config;
    }
}
