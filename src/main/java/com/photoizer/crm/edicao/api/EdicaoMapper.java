package com.photoizer.crm.edicao.api;

import com.photoizer.crm.edicao.model.Edicao;
import com.photoizer.crm.edicao.model.FotoEdicao;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper para o módulo de edição.
 * Elimina mapeamento manual duplicado em EdicaoResponse.of() e FotoEdicaoResponse.of().
 * Segue padrão já adotado no módulo agenda (AgendamentoMapper).
 */
@Mapper(componentModel = "spring")
public interface EdicaoMapper {

    @Mapping(target = "status", expression = "java(e.getStatus().name())")
    @Mapping(target = "fotografoId", expression = "java(e.getFotografo() != null ? e.getFotografo().getId() : null)")
    @Mapping(target = "fotografoNome", expression = "java(e.getFotografo() != null ? e.getFotografo().getNome() : null)")
    @Mapping(target = "editorId", expression = "java(e.getEditor() != null ? e.getEditor().getId() : null)")
    @Mapping(target = "editorNome", expression = "java(e.getEditor() != null ? e.getEditor().getNome() : null)")
    @Mapping(target = "createdAt", source = "e.auditInfo.createdAt")
    @Mapping(target = "updatedAt", source = "e.auditInfo.updatedAt")
    EdicaoResponse toResponse(Edicao e, int totalRaw, int totalEditadas);

    @Mapping(target = "status", expression = "java(f.getStatus().name())")
    @Mapping(target = "createdAt", source = "f.auditInfo.createdAt")
    FotoEdicaoResponse toResponse(FotoEdicao f);
}
