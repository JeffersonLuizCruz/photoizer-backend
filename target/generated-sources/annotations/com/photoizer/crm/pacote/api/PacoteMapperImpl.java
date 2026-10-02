package com.photoizer.crm.pacote.api;

import com.photoizer.crm.pacote.model.Pacote;
import com.photoizer.crm.shared.model.AuditInfo;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-10-01T23:42:39-0300",
    comments = "version: 1.6.3, compiler: javac, environment: Java 26.0.1 (Homebrew)"
)
@Component
public class PacoteMapperImpl implements PacoteMapper {

    @Override
    public PacoteResponse toResponse(Pacote pacote) {
        if ( pacote == null ) {
            return null;
        }

        LocalDateTime createdAt = null;
        LocalDateTime updatedAt = null;
        UUID id = null;
        String nome = null;
        String descricao = null;
        int quantidadeFotos = 0;
        int quantidadeVideos = 0;
        BigDecimal valorBase = null;
        BigDecimal precoFotoExtra = null;
        String imagemCapa = null;
        String beneficios = null;
        String duracaoEstimada = null;
        boolean bloqueiaDiaInteiro = false;
        boolean ativo = false;
        Integer diasParaEntrega = null;

        createdAt = pacoteAuditInfoCreatedAt( pacote );
        updatedAt = pacoteAuditInfoUpdatedAt( pacote );
        id = pacote.getId();
        nome = pacote.getNome();
        descricao = pacote.getDescricao();
        if ( pacote.getQuantidadeFotos() != null ) {
            quantidadeFotos = pacote.getQuantidadeFotos();
        }
        if ( pacote.getQuantidadeVideos() != null ) {
            quantidadeVideos = pacote.getQuantidadeVideos();
        }
        valorBase = pacote.getValorBase();
        precoFotoExtra = pacote.getPrecoFotoExtra();
        imagemCapa = pacote.getImagemCapa();
        beneficios = pacote.getBeneficios();
        duracaoEstimada = pacote.getDuracaoEstimada();
        if ( pacote.getBloqueiaDiaInteiro() != null ) {
            bloqueiaDiaInteiro = pacote.getBloqueiaDiaInteiro();
        }
        if ( pacote.getAtivo() != null ) {
            ativo = pacote.getAtivo();
        }
        diasParaEntrega = pacote.getDiasParaEntrega();

        PacoteResponse pacoteResponse = new PacoteResponse( id, nome, descricao, quantidadeFotos, quantidadeVideos, valorBase, precoFotoExtra, imagemCapa, beneficios, duracaoEstimada, bloqueiaDiaInteiro, ativo, diasParaEntrega, createdAt, updatedAt );

        return pacoteResponse;
    }

    @Override
    public Pacote toEntity(PacoteRequest request) {
        if ( request == null ) {
            return null;
        }

        Pacote.PacoteBuilder pacote = Pacote.builder();

        if ( request.precoFotoExtra() != null ) {
            pacote.precoFotoExtra( request.precoFotoExtra() );
        }
        else {
            pacote.precoFotoExtra( new BigDecimal( "15" ) );
        }
        pacote.nome( request.nome() );
        pacote.descricao( request.descricao() );
        pacote.quantidadeFotos( request.quantidadeFotos() );
        pacote.quantidadeVideos( request.quantidadeVideos() );
        pacote.valorBase( request.valorBase() );
        pacote.imagemCapa( request.imagemCapa() );
        pacote.beneficios( request.beneficios() );
        pacote.duracaoEstimada( request.duracaoEstimada() );
        pacote.bloqueiaDiaInteiro( request.bloqueiaDiaInteiro() );
        pacote.ativo( request.ativo() );
        pacote.diasParaEntrega( request.diasParaEntrega() );

        return pacote.build();
    }

    @Override
    public void updateEntity(PacoteRequest request, Pacote pacote) {
        if ( request == null ) {
            return;
        }

        if ( request.nome() != null ) {
            pacote.setNome( request.nome() );
        }
        if ( request.descricao() != null ) {
            pacote.setDescricao( request.descricao() );
        }
        pacote.setQuantidadeFotos( request.quantidadeFotos() );
        pacote.setQuantidadeVideos( request.quantidadeVideos() );
        if ( request.valorBase() != null ) {
            pacote.setValorBase( request.valorBase() );
        }
        if ( request.precoFotoExtra() != null ) {
            pacote.setPrecoFotoExtra( request.precoFotoExtra() );
        }
        if ( request.imagemCapa() != null ) {
            pacote.setImagemCapa( request.imagemCapa() );
        }
        if ( request.beneficios() != null ) {
            pacote.setBeneficios( request.beneficios() );
        }
        if ( request.duracaoEstimada() != null ) {
            pacote.setDuracaoEstimada( request.duracaoEstimada() );
        }
        pacote.setBloqueiaDiaInteiro( request.bloqueiaDiaInteiro() );
        pacote.setAtivo( request.ativo() );
        if ( request.diasParaEntrega() != null ) {
            pacote.setDiasParaEntrega( request.diasParaEntrega() );
        }
    }

    private LocalDateTime pacoteAuditInfoCreatedAt(Pacote pacote) {
        AuditInfo auditInfo = pacote.getAuditInfo();
        if ( auditInfo == null ) {
            return null;
        }
        return auditInfo.getCreatedAt();
    }

    private LocalDateTime pacoteAuditInfoUpdatedAt(Pacote pacote) {
        AuditInfo auditInfo = pacote.getAuditInfo();
        if ( auditInfo == null ) {
            return null;
        }
        return auditInfo.getUpdatedAt();
    }
}
