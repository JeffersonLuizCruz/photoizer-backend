package com.photoizer.crm.cliente.repository;

import com.photoizer.crm.cliente.model.Cliente;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;

/**
 * Specification para busca unificada de clientes.
 * Padrão Specification - flexibilidade e reutilização de critérios de busca.
 * 
 * Substitui a lógica fragmentada em ClienteService.buscarPorSearch() e
 * listarPaginado(), eliminando queries duplicadas e distinct() em memória.
 */
public class ClienteSpecification {

    private ClienteSpecification() {
        throw new UnsupportedOperationException("Classe utilitária");
    }

    /**
     * Cria Specification para busca por nome OU telefone.
     * Case-insensitive para nome, contains para ambos.
     * 
     * @param search Termo de busca (pode ser null/vazio)
     * @return Specification<Cliente> ou null se search for vazio
     */
    public static Specification<Cliente> buscarPorNomeOuTelefone(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return null;
            }

            var predicates = new ArrayList<Predicate>();

            // Busca por telefone (contém)
            if (!search.isBlank()) {
                predicates.add(cb.like(
                    cb.lower(root.get("telefone")),
                    "%" + search.toLowerCase() + "%"
                ));
            }

            // Busca por nome (contém, case-insensitive)
            if (!search.isBlank()) {
                predicates.add(cb.like(
                    cb.lower(root.get("nome")),
                    "%" + search.toLowerCase() + "%"
                ));
            }

            // OU entre os critérios
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Cria Specification para busca exata por telefone.
     * Usado quando se busca por telefone específico.
     */
    public static Specification<Cliente> buscarPorTelefoneExato(String telefone) {
        return (root, query, cb) -> {
            if (telefone == null || telefone.isBlank()) {
                return null;
            }
            return cb.equal(root.get("telefone"), telefone);
        };
    }

    /**
     * Cria Specification para busca por email.
     */
    public static Specification<Cliente> buscarPorEmail(String email) {
        return (root, query, cb) -> {
            if (email == null || email.isBlank()) {
                return null;
            }
            return cb.equal(cb.lower(root.get("email")), email.toLowerCase());
        };
    }

    /**
     * Cria Specification para busca por CPF.
     */
    public static Specification<Cliente> buscarPorCpf(String cpf) {
        return (root, query, cb) -> {
            if (cpf == null || cpf.isBlank()) {
                return null;
            }
            return cb.equal(root.get("cpf"), cpf);
        };
    }
}
