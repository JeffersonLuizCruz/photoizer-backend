package com.photoizer.crm.despesa.repository;

import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.despesa.model.StatusDespesa;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Specification Pattern — encapsula regras de busca dinâmica de despesas.
 *
 * Design Pattern: JPA Specification (Spring Data)
 *
 * Motivo: A Specification inline no DespesaService (8 parâmetros + whitelist
 * manual de sort em string) é difícil de testar, reutilizar e manter.
 * Esta classe centraliza as regras de filtro em factory methods estáticos,
 * tornando o código mais legível e facilitando testes unitários.
 *
 * Whitelist de colunas de sort é validada por enum implicitly (lista fixa),
 * evitando injeção de ordenação via string arbitrária.
 */
public final class DespesaSpecification {

    private DespesaSpecification() {}

    private static final List<String> SORT_COLUMNS = List.of("data", "valor", "descricao", "categoria");

    public static Specification<Despesa> comFiltros(LocalDate dataInicio, LocalDate dataFim,
                                                     UUID categoriaId, StatusDespesa status,
                                                     UUID agendamentoId, UUID fotografoId) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            if (dataInicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("data"), dataInicio));
            }
            if (dataFim != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("data"), dataFim));
            }
            if (categoriaId != null) {
                predicates.add(cb.equal(root.get("categoriaRef").get("id"), categoriaId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (agendamentoId != null) {
                predicates.add(cb.equal(root.get("agendamentoId"), agendamentoId));
            }
            if (fotografoId != null) {
                predicates.add(cb.equal(root.get("fotografoId"), fotografoId));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    public static Sort parseSort(String sortBy, String sortDir) {
        var coluna = (sortBy != null && !sortBy.isBlank() && SORT_COLUMNS.contains(sortBy)) ? sortBy : "data";
        var direcao = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direcao, coluna);
    }
}
