package com.photoizer.crm.despesa.service;

/*
 * REFACTORED — DespesaCategoriaService (Extraído de DespesaService)
 *
 * Design Pattern: Single Responsibility Principle (SRP) / Service Layer Separation
 *
 * Motivo: DespesaService (312 linhas) misturava CRUD de despesas com CRUD de categorias,
 * violando o SRP. Categorias de despesa são um conceito de domínio independente que
 * merece seu próprio service. Isso facilita:
 * - Testes unitários (mock menor, escopo mais coeso)
 * - Manutenção (mudanças em categorias não afetam despesas)
 * - Reuso (outros módulos podem usar categorias sem depender do service de despesas)
 *
 * Segue o padrão do projeto onde services são @Service com injeção via construtor.
 */

import com.photoizer.crm.despesa.api.DespesaCategoriaRequest;
import com.photoizer.crm.despesa.exception.CategoriaDespesaNaoEncontradaException;
import com.photoizer.crm.despesa.exception.CategoriaDuplicadaException;
import com.photoizer.crm.despesa.model.DespesaCategoria;
import com.photoizer.crm.despesa.repository.DespesaCategoriaRepository;
import com.photoizer.crm.despesa.repository.DespesaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DespesaCategoriaService {

    private final DespesaCategoriaRepository categoriaRepository;
    private final DespesaRepository despesaRepository;

    public DespesaCategoriaService(DespesaCategoriaRepository categoriaRepository,
                                   DespesaRepository despesaRepository) {
        this.categoriaRepository = categoriaRepository;
        this.despesaRepository = despesaRepository;
    }

    @Transactional(readOnly = true)
    public List<DespesaCategoria> listar(Boolean ativas) {
        return ativas != null && ativas
            ? categoriaRepository.findByAtivoTrueOrderByOrdemAscNomeAsc()
            : categoriaRepository.findAll().stream()
                .sorted((a, b) -> {
                    var cmp = Integer.compare(
                        a.getOrdem() != null ? a.getOrdem() : Integer.MAX_VALUE,
                        b.getOrdem() != null ? b.getOrdem() : Integer.MAX_VALUE);
                    return cmp != 0 ? cmp : a.getNome().compareToIgnoreCase(b.getNome());
                })
                .toList();
    }

    public DespesaCategoria criar(DespesaCategoriaRequest request) {
        categoriaRepository.findByNomeIgnoreCase(request.nome().trim())
            .ifPresent(c -> {
                throw new CategoriaDuplicadaException(request.nome().trim());
            });
        var categoria = DespesaCategoria.builder()
            .nome(request.nome().trim())
            .cor(request.cor())
            .ativo(request.ativo() != null ? request.ativo() : true)
            .ordem(request.ordem())
            .build();
        return categoriaRepository.save(categoria);
    }

    public DespesaCategoria atualizar(UUID id, DespesaCategoriaRequest request) {
        var categoria = categoriaRepository.findById(id)
            .orElseThrow(() -> new CategoriaDespesaNaoEncontradaException(id));
        var outro = categoriaRepository.findByNomeIgnoreCase(request.nome().trim());
        if (outro.isPresent() && !outro.get().getId().equals(id)) {
            throw new CategoriaDuplicadaException(request.nome().trim());
        }
        categoria.setNome(request.nome().trim());
        categoria.setCor(request.cor());
        categoria.setAtivo(request.ativo() != null ? request.ativo() : categoria.getAtivo());
        categoria.setOrdem(request.ordem());
        return categoriaRepository.save(categoria);
    }

    public void remover(UUID id) {
        var categoria = categoriaRepository.findById(id)
            .orElseThrow(() -> new CategoriaDespesaNaoEncontradaException(id));
        long qtd = despesaRepository.countByCategoriaRefId(id);
        if (qtd > 0) {
            categoria.setAtivo(false);
            categoriaRepository.save(categoria);
            return;
        }
        categoriaRepository.delete(categoria);
    }

    public long contarDespesas(UUID categoriaId) {
        return despesaRepository.countByCategoriaRefId(categoriaId);
    }
}
