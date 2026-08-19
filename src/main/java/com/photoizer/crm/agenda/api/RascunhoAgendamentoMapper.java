package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.RascunhoAgendamento;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RascunhoAgendamentoMapper {

    @Mapping(target = "data", expression = "java(rascunho.getData() != null ? rascunho.getData().toString() : null)")
    RascunhoAgendamentoResponse toResponse(RascunhoAgendamento rascunho);
}