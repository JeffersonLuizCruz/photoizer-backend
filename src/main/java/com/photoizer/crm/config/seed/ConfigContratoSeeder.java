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
        atualizarTemplateComProfissionais();
        atualizarTemplateComFotografoResponsavel();
        atualizarTemplateComDeslocamento();
    }

    private void seedConfigsContrato() {
        List.of(
            ConfigKey.NOME_FOTOGRAFO,
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

    /**
     * Normaliza o bloco de profissionais do ensaio para o placeholder único
     * {@code {{profissionaisEnsaio}}} (renderizado como lista e omitido quando vazio).
     */
    private void atualizarTemplateComProfissionais() {
        configuracaoRepository.findById(ConfigKey.CONTRATO_TEMPLATE.getKey()).ifPresent(t -> {
            var valor = t.getValor();
            if (valor.contains("{{profissionaisEnsaio}}")) {
                return;
            }
            var atualizado = valor.contains("{{fotografosEnsaio}}")
                ? valor.replace("Profissionais do ensaio: {{fotografosEnsaio}}", "{{profissionaisEnsaio}}")
                       .replace("{{fotografosEnsaio}}", "{{profissionaisEnsaio}}")
                : valor.replace(
                    "Local do ensaio: {{localEnsaio}}",
                    "Local do ensaio: {{localEnsaio}}\n{{profissionaisEnsaio}}");
            if (!atualizado.equals(valor)) {
                t.setValor(atualizado);
                configuracaoRepository.save(t);
                log.info("Template de contrato atualizado com bloco de profissionais do ensaio");
            }
        });
    }

    private void atualizarTemplateComFotografoResponsavel() {
        configuracaoRepository.findById(ConfigKey.CONTRATO_TEMPLATE.getKey()).ifPresent(t -> {
            if (!t.getValor().contains("{{fotografoResponsavel}}")) {
                t.setValor(t.getValor().replace(
                    "Local do ensaio: {{localEnsaio}}",
                    "Local do ensaio: {{localEnsaio}}\nFotógrafo responsável: {{fotografoResponsavel}}"));
                configuracaoRepository.save(t);
                log.info("Template de contrato atualizado com placeholder de fotógrafo responsável");
            }
        });
    }

    private void atualizarTemplateComDeslocamento() {
        configuracaoRepository.findById(ConfigKey.CONTRATO_TEMPLATE.getKey()).ifPresent(t -> {
            if (!t.getValor().contains("{{taxaDeslocamento}}")) {
                t.setValor(t.getValor().replace(
                    "Valor total do serviço: {{valorTotal}}",
                    "Valor do deslocamento: {{taxaDeslocamento}}\nValor total do serviço: {{valorTotal}}"));
                configuracaoRepository.save(t);
                log.info("Template de contrato atualizado com placeholder de deslocamento");
            }
        });
    }
}
