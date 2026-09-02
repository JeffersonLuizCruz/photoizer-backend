package com.photoizer.crm.edicao.api;

import com.photoizer.crm.edicao.model.Edicao;
import com.photoizer.crm.edicao.model.FotoEdicao;
import com.photoizer.crm.shared.model.AuditInfo;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-28T23:12:19-0300",
    comments = "version: 1.6.3, compiler: javac, environment: Java 25.0.4 (Amazon.com Inc.)"
)
@Component
public class EdicaoMapperImpl implements EdicaoMapper {

    @Override
    public EdicaoResponse toResponse(Edicao e, int totalRaw, int totalEditadas) {
        if ( e == null ) {
            return null;
        }

        LocalDateTime createdAt = null;
        LocalDateTime updatedAt = null;
        UUID id = null;
        UUID agendamentoId = null;
        LocalDateTime dataEnvioRaw = null;
        LocalDateTime dataEnvioEditado = null;
        String observacoes = null;
        if ( e != null ) {
            createdAt = eAuditInfoCreatedAt( e );
            updatedAt = eAuditInfoUpdatedAt( e );
            id = e.getId();
            agendamentoId = e.getAgendamentoId();
            dataEnvioRaw = e.getDataEnvioRaw();
            dataEnvioEditado = e.getDataEnvioEditado();
            observacoes = e.getObservacoes();
        }

        String status = e.getStatus().name();
        UUID fotografoId = e.getFotografo() != null ? e.getFotografo().getId() : null;
        String fotografoNome = e.getFotografo() != null ? e.getFotografo().getNome() : null;
        UUID editorId = e.getEditor() != null ? e.getEditor().getId() : null;
        String editorNome = e.getEditor() != null ? e.getEditor().getNome() : null;
        int totalFotosRaw = 0;
        int totalFotosEditadas = 0;

        EdicaoResponse edicaoResponse = new EdicaoResponse( id, agendamentoId, status, fotografoId, fotografoNome, editorId, editorNome, dataEnvioRaw, dataEnvioEditado, observacoes, totalFotosRaw, totalFotosEditadas, createdAt, updatedAt );

        return edicaoResponse;
    }

    @Override
    public FotoEdicaoResponse toResponse(FotoEdicao f) {
        if ( f == null ) {
            return null;
        }

        LocalDateTime createdAt = null;
        UUID id = null;
        UUID edicaoId = null;
        String rawFileName = null;
        String editedFileName = null;
        int ordem = 0;
        Boolean aprovado = null;
        String comentario = null;

        createdAt = fAuditInfoCreatedAt( f );
        id = f.getId();
        edicaoId = f.getEdicaoId();
        rawFileName = f.getRawFileName();
        editedFileName = f.getEditedFileName();
        ordem = f.getOrdem();
        aprovado = f.getAprovado();
        comentario = f.getComentario();

        String status = f.getStatus().name();
        String rawDownloadUrl = null;
        String rawPreviewUrl = null;
        String editedDownloadUrl = null;
        String editedPreviewUrl = null;

        FotoEdicaoResponse fotoEdicaoResponse = new FotoEdicaoResponse( id, edicaoId, rawFileName, rawDownloadUrl, rawPreviewUrl, editedFileName, editedDownloadUrl, editedPreviewUrl, status, ordem, createdAt, aprovado, comentario );

        return fotoEdicaoResponse;
    }

    private LocalDateTime eAuditInfoCreatedAt(Edicao edicao) {
        AuditInfo auditInfo = edicao.getAuditInfo();
        if ( auditInfo == null ) {
            return null;
        }
        return auditInfo.getCreatedAt();
    }

    private LocalDateTime eAuditInfoUpdatedAt(Edicao edicao) {
        AuditInfo auditInfo = edicao.getAuditInfo();
        if ( auditInfo == null ) {
            return null;
        }
        return auditInfo.getUpdatedAt();
    }

    private LocalDateTime fAuditInfoCreatedAt(FotoEdicao fotoEdicao) {
        AuditInfo auditInfo = fotoEdicao.getAuditInfo();
        if ( auditInfo == null ) {
            return null;
        }
        return auditInfo.getCreatedAt();
    }
}
