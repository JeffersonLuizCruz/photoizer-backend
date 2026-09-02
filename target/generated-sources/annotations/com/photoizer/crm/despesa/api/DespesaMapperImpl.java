package com.photoizer.crm.despesa.api;

import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.despesa.model.DespesaCategoria;
import com.photoizer.crm.despesa.model.RecorrenciaDespesa;
import com.photoizer.crm.despesa.model.StatusDespesa;
import com.photoizer.crm.shared.model.FormaPagamento;
import java.math.BigDecimal;
import java.time.LocalDate;
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
public class DespesaMapperImpl implements DespesaMapper {

    @Override
    public DespesaResponse toResponse(Despesa d) {
        if ( d == null ) {
            return null;
        }

        UUID id = null;
        String descricao = null;
        BigDecimal valor = null;
        LocalDate data = null;
        FormaPagamento formaPagamento = null;
        StatusDespesa status = null;
        RecorrenciaDespesa recorrencia = null;
        LocalDate dataProximaGeracao = null;
        UUID geradaDeId = null;
        UUID agendamentoId = null;
        UUID fotografoId = null;
        LocalDateTime dataPagamento = null;
        String urlComprovante = null;
        String observacao = null;

        id = d.getId();
        descricao = d.getDescricao();
        valor = d.getValor();
        data = d.getData();
        formaPagamento = d.getFormaPagamento();
        status = d.getStatus();
        recorrencia = d.getRecorrencia();
        dataProximaGeracao = d.getDataProximaGeracao();
        geradaDeId = d.getGeradaDeId();
        agendamentoId = d.getAgendamentoId();
        fotografoId = d.getFotografoId();
        dataPagamento = d.getDataPagamento();
        urlComprovante = d.getUrlComprovante();
        observacao = d.getObservacao();

        UUID categoriaId = d.getCategoriaRef() != null ? d.getCategoriaRef().getId() : null;
        String categoria = d.getCategoriaRef() != null ? d.getCategoriaRef().getNome() : d.getCategoria();
        String cor = d.getCategoriaRef() != null ? d.getCategoriaRef().getCor() : null;

        DespesaResponse despesaResponse = new DespesaResponse( id, descricao, valor, categoriaId, categoria, cor, data, formaPagamento, status, recorrencia, dataProximaGeracao, geradaDeId, agendamentoId, fotografoId, dataPagamento, urlComprovante, observacao );

        return despesaResponse;
    }

    @Override
    public DespesaCategoriaResponse toCategoriaResponse(DespesaCategoria c, long qtdDespesas) {
        if ( c == null ) {
            return null;
        }

        long qtdDespesas1 = 0L;
        qtdDespesas1 = qtdDespesas;

        UUID id = null;
        String nome = c.getNome();
        String cor = c.getCor();
        Boolean ativo = c.getAtivo();
        Integer ordem = c.getOrdem();

        DespesaCategoriaResponse despesaCategoriaResponse = new DespesaCategoriaResponse( id, nome, cor, ativo, ordem, qtdDespesas1 );

        return despesaCategoriaResponse;
    }
}
