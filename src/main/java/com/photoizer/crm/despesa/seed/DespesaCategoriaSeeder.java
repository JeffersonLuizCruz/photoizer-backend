package com.photoizer.crm.despesa.seed;

import com.photoizer.crm.despesa.model.DespesaCategoria;
import com.photoizer.crm.despesa.model.StatusDespesa;
import com.photoizer.crm.despesa.model.RecorrenciaDespesa;
import com.photoizer.crm.despesa.repository.DespesaCategoriaRepository;
import com.photoizer.crm.despesa.repository.DespesaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * PATTERN: Composite Seeder (CommandLineRunner + @Order)
 *
 * Seeder responsável por criar categorias de despesa e fazer backfill
 * de despesas legadas sem referência de categoria.
 *
 * Cada módulo é dono dos seus dados semeados — responsabilidade única.
 *
 * Idempotência:
 * - Categorias: verifica count() > 0 antes de inserir
 * - Backfill: filtra apenas despesas com categoriaRef == null
 *
 * @Order(30) — independente de outros seeders.
 */
@Component
@Order(30)
public class DespesaCategoriaSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DespesaCategoriaSeeder.class);

    private final DespesaCategoriaRepository despesaCategoriaRepository;
    private final DespesaRepository despesaRepository;

    public DespesaCategoriaSeeder(DespesaCategoriaRepository despesaCategoriaRepository,
                                  DespesaRepository despesaRepository) {
        this.despesaCategoriaRepository = despesaCategoriaRepository;
        this.despesaRepository = despesaRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedCategoriasDespesa();
        backfillDespesasLegadas();
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
