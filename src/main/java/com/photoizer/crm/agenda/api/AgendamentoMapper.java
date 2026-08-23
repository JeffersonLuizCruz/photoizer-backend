package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring")
public interface AgendamentoMapper {

    @Mapping(target = "clienteId", source = "agendamento.cliente.id")
    @Mapping(target = "clienteNome", source = "agendamento.cliente.nome")
    @Mapping(target = "clienteTelefone", source = "agendamento.cliente.telefone")
    @Mapping(target = "clienteEmail", source = "agendamento.cliente.email")
    @Mapping(target = "clienteCpf", source = "agendamento.cliente.cpf")
    @Mapping(target = "clienteCidade", source = "agendamento.cliente.cidade")
    @Mapping(target = "clienteEstado", source = "agendamento.cliente.estado")
    @Mapping(target = "pacoteId", source = "agendamento.pacote.id")
    @Mapping(target = "pacoteNome", source = "agendamento.pacote.nome")
    @Mapping(target = "editorId", source = "agendamento.editor.id")
    @Mapping(target = "editorNome", source = "agendamento.editor.nome")
    @Mapping(target = "fotografoId", source = "agendamento.fotografo.id")
    @Mapping(target = "fotografoNome", source = "agendamento.fotografo.nome")
    @Mapping(target = "valorPacote", expression = "java(agendamento.getValorTotal().subtract(agendamento.getTaxaDeslocamento()))")
    @Mapping(target = "saldoDevedor", expression = "java(agendamento.getValorTotalFinal().subtract(agendamento.getValorEntradaPago()))")
    @Mapping(target = "status", expression = "java(agendamento.getStatus().name())")
    @Mapping(target = "custoDeslocamento", defaultValue = "0")
    @Mapping(target = "repassarDeslocamento", defaultValue = "true")
    @Mapping(target = "percentualEntrada", defaultValue = "30")
    @Mapping(target = "fotografos", source = "fotografos")
    @Mapping(target = "valorComissao", source = "valorComissao")
    @Mapping(target = "indicadorNome", source = "indicadorNome")
    @Mapping(target = "statusComissao", source = "statusComissao")
    AgendamentoResponse toResponse(Agendamento agendamento,
                                   List<AgendamentoFotografo> fotografos,
                                   BigDecimal valorComissao,
                                   String indicadorNome,
                                   String statusComissao);

    @Mapping(target = "fotografoId", source = "fotografo.id")
    @Mapping(target = "fotografoNome", source = "fotografo.nome")
    @Mapping(target = "tipoValor", defaultValue = "FIXO")
    AgendamentoResponse.FotografoNoAgendamento toFotografoNoAgendamento(AgendamentoFotografo fotografos);
}