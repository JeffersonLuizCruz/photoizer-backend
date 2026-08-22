package com.photoizer.crm.shared.config;

import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.config.model.Configuracao;
import com.photoizer.crm.config.repository.ConfiguracaoRepository;
import com.photoizer.crm.contrato.service.ContratoTemplateService;
import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.despesa.model.DespesaCategoria;
import com.photoizer.crm.despesa.model.StatusDespesa;
import com.photoizer.crm.despesa.model.RecorrenciaDespesa;
import com.photoizer.crm.despesa.repository.DespesaCategoriaRepository;
import com.photoizer.crm.despesa.repository.DespesaRepository;
import com.photoizer.crm.indicador.repository.IndicadorRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final ConfiguracaoRepository configuracaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final IndicadorRepository indicadorRepository;
    private final DespesaCategoriaRepository despesaCategoriaRepository;
    private final DespesaRepository despesaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public DataSeeder(UserRepository userRepository,
                      ConfiguracaoRepository configuracaoRepository,
                      PasswordEncoder passwordEncoder,
                      IndicadorRepository indicadorRepository,
                      DespesaCategoriaRepository despesaCategoriaRepository,
                      DespesaRepository despesaRepository) {
        this.userRepository = userRepository;
        this.configuracaoRepository = configuracaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.indicadorRepository = indicadorRepository;
        this.despesaCategoriaRepository = despesaCategoriaRepository;
        this.despesaRepository = despesaRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() == 0) {
            userRepository.saveAll(List.of(
                new User("admin@photoizer.com", passwordEncoder.encode("dev123"), "Administrador", Papel.ADMIN),
                new User("carol@photoizer.com", passwordEncoder.encode("dev123"), "Carol (Fotógrafa)", Papel.FOTOGRAFO),
                new User("joao@photoizer.com", passwordEncoder.encode("dev123"), "João (Editor)", Papel.EDITOR),
                new User("maria@photoizer.com", passwordEncoder.encode("dev123"), "Maria (Assistente)", Papel.EDITOR),
                new User("agendador@photoizer.com", passwordEncoder.encode("dev123"), "Lucas (Agendador)", Papel.AGENDADOR)
            ));
        }

        if (configuracaoRepository.count() == 0) {
            var configs = List.of(
                createConfig(ConfigKey.VALOR_FOTO_EXTRA),
                createConfig(ConfigKey.VALOR_VIDEO_EXTRA),
                createConfig(ConfigKey.PERCENTUAL_COMISSAO),
                createConfig(ConfigKey.PERCENTUAL_ENTRADA),
                createConfig(ConfigKey.TAXA_DESLOCAMENTO)
            );
            configuracaoRepository.saveAll(configs);
        }

        limparIndicadoresDuplicados();
        seedCategoriasDespesa();
        backfillDespesasLegadas();
        seedConfigsContrato();
    }

    private void seedConfigsContrato() {
        List.of(
            new ConfigKey[]{ConfigKey.NOME_CONTRATADA, null},
            new ConfigKey[]{ConfigKey.CNPJ_CONTRATADA, null},
            new ConfigKey[]{ConfigKey.ENDERECO_CONTRATADA, null},
            new ConfigKey[]{ConfigKey.PIX_CHAVE, null},
            new ConfigKey[]{ConfigKey.PIX_TIPO_CHAVE, null},
            new ConfigKey[]{ConfigKey.CONTRATO_DIAS_VALIDADE, null}
        ).forEach(pair -> {
            var key = pair[0];
            if (!configuracaoRepository.existsById(key.getKey())) {
                var config = new Configuracao();
                config.setChave(key.getKey());
                config.setValor(key.getDefaultValue());
                configuracaoRepository.save(config);
            }
        });

        if (!configuracaoRepository.existsById(ConfigKey.CONTRATO_TEMPLATE.getKey())) {
            var template = new Configuracao();
            template.setChave(ConfigKey.CONTRATO_TEMPLATE.getKey());
            template.setValor(ContratoTemplateService.TEMPLATE_PADRAO);
            configuracaoRepository.save(template);
        }

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

    private Configuracao createConfig(ConfigKey key) {
        var config = new Configuracao();
        config.setChave(key.getKey());
        config.setValor(key.getDefaultValue());
        return config;
    }

    private void limparIndicadoresDuplicados() {
        var duplicados = entityManager.createQuery(
            "SELECT i.nome, i.telefone FROM Indicador i GROUP BY i.nome, i.telefone HAVING COUNT(i) > 1",
            Object[].class
        ).getResultList();

        if (!duplicados.isEmpty()) {
            int totalRemovidos = 0;
            for (var par : duplicados) {
                var nome = (String) ((Object[]) par)[0];
                var telefone = (String) ((Object[]) par)[1];
                var indicadores = indicadorRepository.findAllByNomeAndTelefone(nome, telefone);
                indicadores.sort(Comparator.comparing(
                    i -> i.getAuditInfo().getCreatedAt() != null ? i.getAuditInfo().getCreatedAt() : LocalDateTime.MIN));
                var manter = indicadores.removeLast();
                for (var remover : indicadores) {
                    indicadorRepository.delete(remover);
                }
                log.warn("Indicadores duplicados '{}' ({}): mantido ID {}, removidos {} registro(s)",
                    nome, telefone, manter.getId(), indicadores.size());
                totalRemovidos += indicadores.size();
            }
            log.info("Limpeza de indicadores duplicados concluída: {} registros removidos", totalRemovidos);
        }
    }

    private void seedCategoriasDespesa() {
        if (despesaCategoriaRepository.count() > 0) return;

        var categorias = List.of(
            new String[]{"Equipamento", "#64748b"},
            new String[]{"Software e Assinaturas", "#6366f1"},
            new String[]{"Marketing e Publicidade", "#f59e0b"},
            new String[]{"Deslocamento", "#10b981"},
            new String[]{"Alimentação", "#e1749a"},
            new String[]{"Assistente / Equipe", "#8b5cf6"},
            new String[]{"Local / Estúdio", "#ec4899"},
            new String[]{"Material de Entrega", "#14b8a6"},
            new String[]{"Impostos e Taxas", "#f97316"},
            new String[]{"Educação", "#3b82f6"},
            new String[]{"Outros", "#94a3b8"}
        );

        int ordem = 0;
        for (var c : categorias) {
            despesaCategoriaRepository.save(DespesaCategoria.builder()
                .nome(c[0])
                .cor(c[1])
                .ativo(true)
                .ordem(ordem++)
                .build());
        }
        log.info("Categorias de despesa semeadas: {}", categorias.size());
    }

    private void backfillDespesasLegadas() {
        var legadas = despesaRepository.findAll().stream()
            .filter(d -> d.getCategoriaRef() == null)
            .toList();
        if (legadas.isEmpty()) return;

        var categorias = despesaCategoriaRepository.findAll();
        var porNome = categorias.stream()
            .collect(Collectors.toMap(c -> c.getNome().toLowerCase(), c -> c));

        for (var despesa : legadas) {
            var origem = despesa.getCategoria() != null
                ? despesa.getCategoria().toLowerCase()
                : "";
            var categoria = switch (origem) {
                case "manutencao" -> porNome.get("equipamento");
                case "compra" -> porNome.get("outros");
                default -> porNome.get(origem);
            };
            if (categoria == null) categoria = porNome.get("outros");

            despesa.setCategoriaRef(categoria);
            despesa.setCategoria(categoria.getNome());
            if (despesa.getStatus() == null) despesa.setStatus(StatusDespesa.PENDENTE);
            if (despesa.getRecorrencia() == null) despesa.setRecorrencia(RecorrenciaDespesa.UNICA);
            despesaRepository.save(despesa);
        }
        log.info("Backfill de despesas legadas concluído: {} registro(s)", legadas.size());
    }
}
