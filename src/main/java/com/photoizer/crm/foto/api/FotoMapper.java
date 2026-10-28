package com.photoizer.crm.foto.api;

import com.photoizer.crm.foto.model.FotoEnsaio;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.ZoneId;

/**
 * PATTERN: DTO Mapper (MapStruct)
 * Elimina mapeamento manual campo-a-campo nos records FotoEnsaioResponse.
 * Usa expression para enums e null-safe access, seguindo o padrão
 * de AgendamentoMapper, DespesaMapper e EcommerceMapper.
 */
@Mapper(componentModel = "spring")
public interface FotoMapper {

    @Mapping(target = "status", expression = "java(f.getStatus().name())")
    @Mapping(target = "versaoAtual", expression = "java(f.getVersaoAtual().name())")
    @Mapping(target = "temVersaoEditada", expression = "java(f.getEditadaPath() != null)")
    @Mapping(target = "originalUrl", expression = "java(\"/api/v1/agendamentos/\" + f.getAgendamentoId() + \"/fotos/\" + f.getId() + \"/original\")")
    @Mapping(target = "watermarkedUrl", expression = "java(FotoMapper.urlPreview(f))")
    @Mapping(target = "thumbUrl", expression = "java(FotoMapper.urlThumb(f))")
    @Mapping(target = "createdAt", source = "f.auditInfo.createdAt")
    @Mapping(target = "downloadada", expression = "java(f.getDataDownload() != null)")
    FotoEnsaioResponse toResponse(FotoEnsaio f);

    @Mapping(target = "status", expression = "java(f.getStatus().name())")
    @Mapping(target = "versaoAtual", expression = "java(f.getVersaoAtual().name())")
    @Mapping(target = "temVersaoEditada", expression = "java(f.getEditadaPath() != null)")
    @Mapping(target = "originalUrl", expression = "java(null)")
    @Mapping(target = "watermarkedUrl", expression = "java(FotoMapper.urlPreview(f))")
    @Mapping(target = "thumbUrl", expression = "java(FotoMapper.urlThumb(f))")
    @Mapping(target = "createdAt", source = "f.auditInfo.createdAt")
    @Mapping(target = "downloadada", expression = "java(f.getDataDownload() != null)")
    @Mapping(target = "metadataExif", expression = "java(null)")
    @Mapping(target = "editadaFileName", expression = "java(null)")
    FotoEnsaioResponse toPublicResponse(FotoEnsaio f);

    /**
     * Cache-buster das imagens públicas. O {@code updatedAt} muda quando a
     * foto é substituída, editada ou tem a marca regenerada, então o navegador
     * pode guardar a imagem com segurança.
     */
    static String urlPreview(FotoEnsaio f) {
        return "/api/v1/ecommerce/fotos/" + f.getId() + "/watermarked" + versaoQuery(f);
    }

    static String urlThumb(FotoEnsaio f) {
        return "/api/v1/ecommerce/fotos/" + f.getId() + "/thumb" + versaoQuery(f);
    }

    private static String versaoQuery(FotoEnsaio f) {
        var versao = f.getVersaoAtual() == null ? "ORIGINAL" : f.getVersaoAtual().name();
        var atualizadoEm = f.getAuditInfo() == null ? null : f.getAuditInfo().getUpdatedAt();
        var token = atualizadoEm == null ? 0L
            : atualizadoEm.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return "?v=" + versao + "-" + token;
    }
}
