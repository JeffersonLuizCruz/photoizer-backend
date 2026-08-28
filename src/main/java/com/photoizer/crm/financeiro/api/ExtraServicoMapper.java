package com.photoizer.crm.financeiro.api;

import com.photoizer.crm.financeiro.model.ExtraServico;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct Mapper para ExtraServico.
 *
 * Pattern: Mapper Pattern (MapStruct) — substitui construtor manual de DTO.
 */
@Mapper(componentModel = "spring")
public interface ExtraServicoMapper {

    @Mapping(target = "agendamentoId", expression = "java(e.getAgendamento().getId())")
    ExtraServicoResponse toResponse(ExtraServico e);
}
