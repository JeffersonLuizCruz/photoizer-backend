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
 * Seeder responsável por criar as configurações de contrato.
 * Cada módulo é dono dos seus dados semeados — responsabilidade única.
 *
 * Idempotência: verifica existência de cada chave individualmente.
 * @Order(11) — roda após ConfigBaseSeeder (ambos escrevem na mesma tabela).
 */
@Component
@Order(11)
public class ConfigContratoSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigContratoSeeder.class);

    private final ConfiguracaoRepository configuracaoRepository;

    public ConfigContratoSeeder(ConfiguracaoRepository configuracaoRepository) {
        this.configuracaoRepository = configuracaoRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedConfigsContrato();
        seedTemplateContrato();
        atualizarTemplateComFotografos();
    }

    private void seedConfigsContrato() {
        List.of(
            ConfigKey.NOME_CONTRATADA,
            ConfigKey.CNPJ_CONTRATADA,
            ConfigKey.ENDERECO_CONTRATADA,
            ConfigKey.PIX_CHAVE,
            ConfigKey.PIX_TIPO_CHAVE,
            ConfigKey.CONTRATO_DIAS_VALIDADE
        ).forEach(key -> {
            if (!configuracaoRepository.existsById(key.getKey())) {
                var config = new Configuracao();
                config.setChave(key.getKey());
                config.setValor(key.getDefaultValue());
                configuracaoRepository.save(config);
            }
        });
    }

    private void seedTemplateContrato() {
        if (!configuracaoRepository.existsById(ConfigKey.CONTRATO_TEMPLATE.getKey())) {
            var template = new Configuracao();
            template.setChave(ConfigKey.CONTRATO_TEMPLATE.getKey());
            template.setValor(ConfigKey.CONTRATO_TEMPLATE_PADRAO);
            configuracaoRepository.save(template);
            log.info("Template de contrato semeadо");
        }
    }

    private void atualizarTemplateComFotografos() {
        configuracaoRepository.findById(ConfigKey.CONTRATO_TEMPLATE.getKey()).ifPresent(t -> {
            if (!t.getValor().contains("{{fotografosEnsaio}}")) {
                t.setValor(t.getValor().replace(
                    "Endereço completo: {{enderecoEnsaio}}",
                    "Endereço completo: {{enderecoEnsaio}}\nProfissionais do ensaio: {{fotografosEnsaio}}"));
                configuracaoRepository.save(t);
                log.info("Template de contrato atualizado com placeholder de profissionais do ensaio");
            }
        });
    }
}
