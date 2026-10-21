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
        atualizarTemplateComDuracao();
        atualizarTemplateComDescricaoPacote();
        atualizarTemplateComStatusReserva();
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

    /**
     * Insere a linha de duração do ensaio ({@code {{duracaoEnsaio}}}) logo após o
     * horário, nos templates já persistidos que ainda não a possuem.
     */
    private void atualizarTemplateComDuracao() {
        configuracaoRepository.findById(ConfigKey.CONTRATO_TEMPLATE.getKey()).ifPresent(t -> {
            if (t.getValor().contains("{{duracaoEnsaio}}")) {
                return;
            }
            var atualizado = t.getValor().contains("Horário do ensaio: {{horarioEnsaio}}")
                ? t.getValor().replace(
                    "Horário do ensaio: {{horarioEnsaio}}",
                    "Horário do ensaio: {{horarioEnsaio}}\nDuração do ensaio: {{duracaoEnsaio}}")
                : t.getValor().replace(
                    "Data do ensaio: {{dataEnsaio}}",
                    "Data do ensaio: {{dataEnsaio}}\nDuração do ensaio: {{duracaoEnsaio}}");
            if (!atualizado.equals(t.getValor())) {
                t.setValor(atualizado);
                configuracaoRepository.save(t);
                log.info("Template de contrato atualizado com placeholder de duração do ensaio");
            }
        });
    }

    /**
     * Substitui o texto fixo da seção "Pacote Contratado" pelo placeholder
     * {@code {{pacoteDescricao}}}, para que a descrição real do pacote seja
     * exibida nos contratos já persistidos.
     */
    private void atualizarTemplateComDescricaoPacote() {
        configuracaoRepository.findById(ConfigKey.CONTRATO_TEMPLATE.getKey()).ifPresent(t -> {
            if (t.getValor().contains("{{pacoteDescricao}}")) {
                return;
            }
            var atualizado = t.getValor().replace(
                "Inclui: serviço conforme pacote contratado.",
                "Inclui:\n{{pacoteDescricao}}");
            if (!atualizado.equals(t.getValor())) {
                t.setValor(atualizado);
                configuracaoRepository.save(t);
                log.info("Template de contrato atualizado com placeholder de descrição do pacote");
            }
        });
    }

    /**
     * Adiciona o status da reserva ({@code recebida}/{@code a receber}) ao
     * template, deixando de afirmar que a reserva foi paga no modo percentual
     * do agendamento avulso. Atualiza apenas a linha legada padrão, preservando
     * templates customizados que não a contenham.
     */
    private void atualizarTemplateComStatusReserva() {
        configuracaoRepository.findById(ConfigKey.CONTRATO_TEMPLATE.getKey()).ifPresent(t -> {
            if (t.getValor().contains("{{statusReserva}}")) {
                return;
            }
            var atualizado = t.getValor().replace(
                "Valor pago como reserva ({{percentualEntrada}}%): {{valorEntrada}}",
                "Valor da reserva ({{percentualEntrada}}%) - {{statusReserva}}: {{valorEntrada}}");
            if (!atualizado.equals(t.getValor())) {
                t.setValor(atualizado);
                configuracaoRepository.save(t);
                log.info("Template de contrato atualizado com placeholder de status da reserva");
            }
        });
    }
}
