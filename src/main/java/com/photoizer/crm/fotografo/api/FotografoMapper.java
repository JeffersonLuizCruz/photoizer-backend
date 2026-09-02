package com.photoizer.crm.fotografo.api;

import com.photoizer.crm.auth.api.UserResponse;
import com.photoizer.crm.auth.model.User;
import org.mapstruct.Mapper;

/**
 * Mapper MapStruct para conversão de entidades do módulo auth para DTOs
 * do módulo fotografo.
 *
 * Design Pattern: Data Transfer Object (DTO) + Mapper Pattern.
 * Motivo: padronizar conversão entidade→DTO, eliminar factory methods
 * manuais (UserResponse::of), garantir consistência com os 7 módulos
 * que já usam MapStruct no projeto. Facilita manutenção quando campos
 * são adicionados/removidos nas entidades.
 */
@Mapper(componentModel = "spring")
public interface FotografoMapper {

    UserResponse toResponse(User user);
}
