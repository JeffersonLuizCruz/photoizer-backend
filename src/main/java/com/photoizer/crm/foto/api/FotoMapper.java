package com.photoizer.crm.foto.api;

import com.photoizer.crm.foto.model.FotoEnsaio;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * PATTERN: DTO Mapper (MapStruct)
 * Elimina mapeamento manual campo-a-campo nos records FotoEnsaioResponse.
 * Usa expression para enums e null-safe access, seguindo o padrão
 * de AgendamentoMapper, DespesaMapper e EcommerceMapper.
 */
@Mapper(componentModel = "spring")
public interface FotoMapper {

    @Mapping(target = "status", expression = "java(f.getStatus().name())")
    @Mapping(target = "originalUrl", expression = "java(\"/api/v1/agendamentos/\" + f.getAgendamentoId() + \"/fotos/\" + f.getId() + \"/original\")")
    @Mapping(target = "watermarkedUrl", expression = "java(\"/api/v1/ecommerce/fotos/\" + f.getId() + \"/watermarked\")")
    @Mapping(target = "thumbUrl", expression = "java(\"/api/v1/agendamentos/\" + f.getAgendamentoId() + \"/fotos/\" + f.getId() + \"/thumb\")")
    @Mapping(target = "createdAt", source = "f.auditInfo.createdAt")
    @Mapping(target = "downloadada", expression = "java(f.getDataDownload() != null)")
    FotoEnsaioResponse toResponse(FotoEnsaio f);

    @Mapping(target = "status", expression = "java(f.getStatus().name())")
    @Mapping(target = "originalUrl", expression = "java(null)")
    @Mapping(target = "watermarkedUrl", expression = "java(\"/api/v1/ecommerce/fotos/\" + f.getId() + \"/watermarked\")")
    @Mapping(target = "thumbUrl", expression = "java(\"/api/v1/ecommerce/fotos/\" + f.getId() + \"/thumb\")")
    @Mapping(target = "createdAt", source = "f.auditInfo.createdAt")
    @Mapping(target = "downloadada", expression = "java(f.getDataDownload() != null)")
    @Mapping(target = "metadataExif", expression = "java(null)")
    FotoEnsaioResponse toPublicResponse(FotoEnsaio f);
}
