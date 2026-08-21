package com.photoizer.crm.despesa.api;

import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.despesa.model.DespesaCategoria;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct Mapper para DTOs do módulo despesa.
 *
 * Design Pattern: Mapper Pattern (MapStruct)
 *
 * Motivo: Substitui os métodos manuais `static of()` nos records DTO.
 * MapStruct gera implementação em compile-time, eliminando:
 * - Bugs de mapeamento (campos esquecidos ou mapeados incorretamente)
 * - Código boilerplate de 17 campos em DespesaResponse
 * - Inconsistências entre criar/update (defaults divergentes)
 *
 * Segue a decisão aprovada no DEBT.md §4.6 e o padrão já adotado
 * no módulo agenda (AgendamentoMapper, RascunhoAgendamentoMapper).
 *
 * A interface gera um bean Spring (`componentModel = "spring"`) que
 * pode ser injetado em controllers e services.
 */
@Mapper(componentModel = "spring")
public interface DespesaMapper {

    @Mapping(target = "categoriaId", expression = "java(d.getCategoriaRef() != null ? d.getCategoriaRef().getId() : null)")
    @Mapping(target = "categoria", expression = "java(d.getCategoriaRef() != null ? d.getCategoriaRef().getNome() : d.getCategoria())")
    @Mapping(target = "cor", expression = "java(d.getCategoriaRef() != null ? d.getCategoriaRef().getCor() : null)")
    DespesaResponse toResponse(Despesa d);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "nome", expression = "java(c.getNome())")
    @Mapping(target = "cor", expression = "java(c.getCor())")
    @Mapping(target = "ativo", expression = "java(c.getAtivo())")
    @Mapping(target = "ordem", expression = "java(c.getOrdem())")
    DespesaCategoriaResponse toCategoriaResponse(DespesaCategoria c, long qtdDespesas);
}
