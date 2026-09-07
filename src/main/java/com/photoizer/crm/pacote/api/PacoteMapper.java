package com.photoizer.crm.pacote.api;

import com.photoizer.crm.pacote.model.Pacote;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.math.BigDecimal;

/**
 * MapStruct Mapper para DTOs do módulo pacote.
 *
 * Design Pattern: Mapper Pattern (MapStruct)
 *
 * Motivo: Substitui os métodos manuais `static of()` no PacoteResponse
 * e o merge manual de 13 campos no PacoteService.atualizar().
 * MapStruct gera implementação em compile-time, eliminando:
 * - Bugs de mapeamento (campos esquecidos ou mapeados incorretamente)
 * - Código boilerplate de 13 campos no merge do atualizar
 * - Inconsistências entre criar/update (defaults divergentes para precoFotoExtra)
 *
 * Regras:
 * - `toResponse`: mapeia @Embedded auditInfo para campos planos do DTO.
 * - `toEntity`: aplica default `precoFotoExtra = 15` se null (criação).
 * - `updateEntity`: usa NullValuePropertyMappingStrategy.IGNORE para
 *   preservar valores existentes quando o campo não é enviado (null).
 *
 * bean Spring (componentModel = "spring") injetado em controllers e services.
 */
@Mapper(componentModel = "spring")
public interface PacoteMapper {

    @Mapping(source = "auditInfo.createdAt", target = "createdAt")
    @Mapping(source = "auditInfo.updatedAt", target = "updatedAt")
    PacoteResponse toResponse(Pacote pacote);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "auditInfo", ignore = true)
    @Mapping(target = "precoFotoExtra", defaultValue = "15")
    Pacote toEntity(PacoteRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "auditInfo", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(PacoteRequest request, @MappingTarget Pacote pacote);
}
