package com.photoizer.crm.foto.api;

import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.shared.model.AuditInfo;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-28T23:12:19-0300",
    comments = "version: 1.6.3, compiler: javac, environment: Java 25.0.4 (Amazon.com Inc.)"
)
@Component
public class FotoMapperImpl implements FotoMapper {

    @Override
    public FotoEnsaioResponse toResponse(FotoEnsaio f) {
        if ( f == null ) {
            return null;
        }

        LocalDateTime createdAt = null;
        UUID id = null;
        UUID agendamentoId = null;
        String fileName = null;
        int ordem = 0;
        boolean selecionadaPacote = false;
        UUID compraExtraId = null;
        String titulo = null;
        String descricao = null;
        List<String> tags = null;
        String categoria = null;
        LocalDate dataSessao = null;
        String metadataExif = null;
        boolean destaque = false;
        UUID fotoEdicaoId = null;
        boolean visivel = false;

        createdAt = fAuditInfoCreatedAt( f );
        id = f.getId();
        agendamentoId = f.getAgendamentoId();
        fileName = f.getFileName();
        ordem = f.getOrdem();
        selecionadaPacote = f.isSelecionadaPacote();
        compraExtraId = f.getCompraExtraId();
        titulo = f.getTitulo();
        descricao = f.getDescricao();
        List<String> list = f.getTags();
        if ( list != null ) {
            tags = new ArrayList<String>( list );
        }
        categoria = f.getCategoria();
        dataSessao = f.getDataSessao();
        metadataExif = f.getMetadataExif();
        destaque = f.isDestaque();
        fotoEdicaoId = f.getFotoEdicaoId();
        visivel = f.isVisivel();

        String status = f.getStatus().name();
        String originalUrl = "/api/v1/agendamentos/" + f.getAgendamentoId() + "/fotos/" + f.getId() + "/original";
        String watermarkedUrl = "/api/v1/ecommerce/fotos/" + f.getId() + "/watermarked";
        String thumbUrl = "/api/v1/agendamentos/" + f.getAgendamentoId() + "/fotos/" + f.getId() + "/thumb";
        boolean downloadada = f.getDataDownload() != null;

        FotoEnsaioResponse fotoEnsaioResponse = new FotoEnsaioResponse( id, agendamentoId, fileName, originalUrl, watermarkedUrl, thumbUrl, ordem, status, selecionadaPacote, downloadada, compraExtraId, createdAt, titulo, descricao, tags, categoria, dataSessao, metadataExif, destaque, fotoEdicaoId, visivel );

        return fotoEnsaioResponse;
    }

    @Override
    public FotoEnsaioResponse toPublicResponse(FotoEnsaio f) {
        if ( f == null ) {
            return null;
        }

        LocalDateTime createdAt = null;
        UUID id = null;
        UUID agendamentoId = null;
        String fileName = null;
        int ordem = 0;
        boolean selecionadaPacote = false;
        UUID compraExtraId = null;
        String titulo = null;
        String descricao = null;
        List<String> tags = null;
        String categoria = null;
        LocalDate dataSessao = null;
        boolean destaque = false;
        UUID fotoEdicaoId = null;
        boolean visivel = false;

        createdAt = fAuditInfoCreatedAt( f );
        id = f.getId();
        agendamentoId = f.getAgendamentoId();
        fileName = f.getFileName();
        ordem = f.getOrdem();
        selecionadaPacote = f.isSelecionadaPacote();
        compraExtraId = f.getCompraExtraId();
        titulo = f.getTitulo();
        descricao = f.getDescricao();
        List<String> list = f.getTags();
        if ( list != null ) {
            tags = new ArrayList<String>( list );
        }
        categoria = f.getCategoria();
        dataSessao = f.getDataSessao();
        destaque = f.isDestaque();
        fotoEdicaoId = f.getFotoEdicaoId();
        visivel = f.isVisivel();

        String status = f.getStatus().name();
        String originalUrl = null;
        String watermarkedUrl = "/api/v1/ecommerce/fotos/" + f.getId() + "/watermarked";
        String thumbUrl = "/api/v1/ecommerce/fotos/" + f.getId() + "/thumb";
        boolean downloadada = f.getDataDownload() != null;
        String metadataExif = null;

        FotoEnsaioResponse fotoEnsaioResponse = new FotoEnsaioResponse( id, agendamentoId, fileName, originalUrl, watermarkedUrl, thumbUrl, ordem, status, selecionadaPacote, downloadada, compraExtraId, createdAt, titulo, descricao, tags, categoria, dataSessao, metadataExif, destaque, fotoEdicaoId, visivel );

        return fotoEnsaioResponse;
    }

    private LocalDateTime fAuditInfoCreatedAt(FotoEnsaio fotoEnsaio) {
        AuditInfo auditInfo = fotoEnsaio.getAuditInfo();
        if ( auditInfo == null ) {
            return null;
        }
        return auditInfo.getCreatedAt();
    }
}
