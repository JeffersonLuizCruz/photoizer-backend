package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.RascunhoAgendamento;
import java.math.BigDecimal;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-28T23:12:19-0300",
    comments = "version: 1.6.3, compiler: javac, environment: Java 25.0.4 (Amazon.com Inc.)"
)
@Component
public class RascunhoAgendamentoMapperImpl implements RascunhoAgendamentoMapper {

    @Override
    public RascunhoAgendamentoResponse toResponse(RascunhoAgendamento rascunho) {
        if ( rascunho == null ) {
            return null;
        }

        UUID id = null;
        String clienteId = null;
        String nome = null;
        String telefone = null;
        String email = null;
        String cpf = null;
        String cidade = null;
        String estado = null;
        String origem = null;
        String pacoteId = null;
        String hora = null;
        String localEnsaio = null;
        String enderecoCompleto = null;
        String editorId = null;
        BigDecimal custoDeslocamento = null;
        Boolean repassarDeslocamento = null;
        Boolean autorizaUsoImagem = null;
        String indicadorId = null;
        String indicadorNome = null;
        String indicadorTelefone = null;
        String observacoes = null;
        Integer currentStep = null;
        String comprovanteName = null;
        Boolean confirmado = null;

        id = rascunho.getId();
        clienteId = rascunho.getClienteId();
        nome = rascunho.getNome();
        telefone = rascunho.getTelefone();
        email = rascunho.getEmail();
        cpf = rascunho.getCpf();
        cidade = rascunho.getCidade();
        estado = rascunho.getEstado();
        origem = rascunho.getOrigem();
        pacoteId = rascunho.getPacoteId();
        hora = rascunho.getHora();
        localEnsaio = rascunho.getLocalEnsaio();
        enderecoCompleto = rascunho.getEnderecoCompleto();
        editorId = rascunho.getEditorId();
        custoDeslocamento = rascunho.getCustoDeslocamento();
        repassarDeslocamento = rascunho.getRepassarDeslocamento();
        autorizaUsoImagem = rascunho.getAutorizaUsoImagem();
        indicadorId = rascunho.getIndicadorId();
        indicadorNome = rascunho.getIndicadorNome();
        indicadorTelefone = rascunho.getIndicadorTelefone();
        observacoes = rascunho.getObservacoes();
        currentStep = rascunho.getCurrentStep();
        comprovanteName = rascunho.getComprovanteName();
        confirmado = rascunho.getConfirmado();

        String data = rascunho.getData() != null ? rascunho.getData().toString() : null;

        RascunhoAgendamentoResponse rascunhoAgendamentoResponse = new RascunhoAgendamentoResponse( id, clienteId, nome, telefone, email, cpf, cidade, estado, origem, pacoteId, data, hora, localEnsaio, enderecoCompleto, editorId, custoDeslocamento, repassarDeslocamento, autorizaUsoImagem, indicadorId, indicadorNome, indicadorTelefone, observacoes, currentStep, comprovanteName, confirmado );

        return rascunhoAgendamentoResponse;
    }
}
