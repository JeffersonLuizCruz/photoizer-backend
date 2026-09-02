package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.Avaliacao;
import com.photoizer.crm.ecommerce.model.CompraExtra;
import com.photoizer.crm.ecommerce.model.FotoComentario;
import com.photoizer.crm.ecommerce.model.Sessao;
import com.photoizer.crm.ecommerce.model.StatusSessao;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-02T17:17:06-0300",
    comments = "version: 1.6.3, compiler: javac, environment: Java 26.0.1 (Homebrew)"
)
@Component
public class EcommerceMapperImpl implements EcommerceMapper {

    @Override
    public CompraExtraResponse toResponse(CompraExtra c) {
        if ( c == null ) {
            return null;
        }

        UUID id = null;
        UUID agendamentoId = null;
        BigDecimal valorTotal = null;
        String urlComprovante = null;
        LocalDateTime dataPagamento = null;
        Integer quantidadeFotos = null;
        String motivoRecusa = null;

        id = c.getId();
        agendamentoId = c.getAgendamentoId();
        valorTotal = c.getValorTotal();
        urlComprovante = c.getUrlComprovante();
        dataPagamento = c.getDataPagamento();
        quantidadeFotos = c.getQuantidadeFotos();
        motivoRecusa = c.getMotivoRecusa();

        String status = c.getStatus().name();
        String metodoPagamento = c.getMetodoPagamento() != null ? c.getMetodoPagamento().name() : null;

        CompraExtraResponse compraExtraResponse = new CompraExtraResponse( id, agendamentoId, valorTotal, status, urlComprovante, dataPagamento, quantidadeFotos, metodoPagamento, motivoRecusa );

        return compraExtraResponse;
    }

    @Override
    public CompraExtraResponse toPublicResponse(CompraExtra c) {
        if ( c == null ) {
            return null;
        }

        UUID id = null;
        UUID agendamentoId = null;
        BigDecimal valorTotal = null;
        LocalDateTime dataPagamento = null;
        Integer quantidadeFotos = null;
        String motivoRecusa = null;

        id = c.getId();
        agendamentoId = c.getAgendamentoId();
        valorTotal = c.getValorTotal();
        dataPagamento = c.getDataPagamento();
        quantidadeFotos = c.getQuantidadeFotos();
        motivoRecusa = c.getMotivoRecusa();

        String status = c.getStatus().name();
        String metodoPagamento = c.getMetodoPagamento() != null ? c.getMetodoPagamento().name() : null;
        String urlComprovante = "null";

        CompraExtraResponse compraExtraResponse = new CompraExtraResponse( id, agendamentoId, valorTotal, status, urlComprovante, dataPagamento, quantidadeFotos, metodoPagamento, motivoRecusa );

        return compraExtraResponse;
    }

    @Override
    public CompraExtraResponse toAdminResponse(CompraExtra c) {
        if ( c == null ) {
            return null;
        }

        UUID id = null;
        UUID agendamentoId = null;
        BigDecimal valorTotal = null;
        LocalDateTime dataPagamento = null;
        Integer quantidadeFotos = null;
        String motivoRecusa = null;

        id = c.getId();
        agendamentoId = c.getAgendamentoId();
        valorTotal = c.getValorTotal();
        dataPagamento = c.getDataPagamento();
        quantidadeFotos = c.getQuantidadeFotos();
        motivoRecusa = c.getMotivoRecusa();

        String status = c.getStatus().name();
        String metodoPagamento = c.getMetodoPagamento() != null ? c.getMetodoPagamento().name() : null;
        String urlComprovante = c.getUrlComprovante() != null ? "/api/v1/admin/ecommerce/compras/" + c.getId() + "/comprovante" : null;

        CompraExtraResponse compraExtraResponse = new CompraExtraResponse( id, agendamentoId, valorTotal, status, urlComprovante, dataPagamento, quantidadeFotos, metodoPagamento, motivoRecusa );

        return compraExtraResponse;
    }

    @Override
    public ComentarioResponse toComentarioResponse(FotoComentario c) {
        if ( c == null ) {
            return null;
        }

        UUID id = null;
        UUID fotoId = null;
        String autorNome = null;
        String mensagem = null;
        boolean lida = false;

        id = c.getId();
        fotoId = c.getFotoId();
        autorNome = c.getAutorNome();
        mensagem = c.getMensagem();
        lida = c.isLida();

        String origem = c.getOrigem().name();
        LocalDateTime createdAt = c.getAuditInfo().getCreatedAt();

        ComentarioResponse comentarioResponse = new ComentarioResponse( id, fotoId, autorNome, mensagem, origem, lida, createdAt );

        return comentarioResponse;
    }

    @Override
    public AvaliacaoResponse toAvaliacaoResponse(Avaliacao a) {
        if ( a == null ) {
            return null;
        }

        UUID id = null;
        UUID clienteId = null;
        UUID agendamentoId = null;
        UUID pacoteId = null;
        int pontuacao = 0;
        String comentario = null;
        boolean depoimento = false;
        boolean aprovado = false;

        id = a.getId();
        clienteId = a.getClienteId();
        agendamentoId = a.getAgendamentoId();
        pacoteId = a.getPacoteId();
        pontuacao = a.getPontuacao();
        comentario = a.getComentario();
        depoimento = a.isDepoimento();
        aprovado = a.isAprovado();

        LocalDateTime createdAt = a.getAuditInfo().getCreatedAt();

        AvaliacaoResponse avaliacaoResponse = new AvaliacaoResponse( id, clienteId, agendamentoId, pacoteId, pontuacao, comentario, depoimento, aprovado, createdAt );

        return avaliacaoResponse;
    }

    @Override
    public SessaoResponse toSessaoResponse(Sessao s) {
        if ( s == null ) {
            return null;
        }

        UUID id = null;
        UUID clienteId = null;
        String nomeSessao = null;
        LocalDate dataRealizacao = null;
        String local = null;
        String descricao = null;
        StatusSessao status = null;

        id = s.getId();
        clienteId = s.getClienteId();
        nomeSessao = s.getNomeSessao();
        dataRealizacao = s.getDataRealizacao();
        local = s.getLocal();
        descricao = s.getDescricao();
        status = s.getStatus();

        LocalDateTime createdAt = s.getAuditInfo().getCreatedAt();

        SessaoResponse sessaoResponse = new SessaoResponse( id, clienteId, nomeSessao, dataRealizacao, local, descricao, status, createdAt );

        return sessaoResponse;
    }
}
