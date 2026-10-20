package com.photoizer.crm.bloqueio.api;

import com.photoizer.crm.bloqueio.model.BloqueioAgenda;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BloqueioAgendaMapper {

    @Mapping(target = "criadoEm", source = "auditInfo.createdAt")
    BloqueioAgendaResponse toResponse(BloqueioAgenda bloqueio);
}
