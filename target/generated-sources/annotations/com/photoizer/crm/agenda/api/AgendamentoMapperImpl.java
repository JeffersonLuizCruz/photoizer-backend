package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.cliente.model.Cliente;
import com.photoizer.crm.pacote.model.Pacote;
import com.photoizer.crm.shared.model.TipoRepasse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-02T17:17:06-0300",
    comments = "version: 1.6.3, compiler: javac, environment: Java 26.0.1 (Homebrew)"
)
@Component
public class AgendamentoMapperImpl implements AgendamentoMapper {

    @Override
    public AgendamentoResponse toResponse(Agendamento agendamento, List<AgendamentoFotografo> fotografos, BigDecimal valorComissao, String indicadorNome, String statusComissao) {
        if ( agendamento == null && fotografos == null && valorComissao == null && indicadorNome == null && statusComissao == null ) {
            return null;
        }

        UUID clienteId = null;
        String clienteNome = null;
        String clienteTelefone = null;
        String clienteEmail = null;
        String clienteCpf = null;
        String clienteCidade = null;
        String clienteEstado = null;
        UUID pacoteId = null;
        String pacoteNome = null;
        UUID editorId = null;
        String editorNome = null;
        UUID fotografoId = null;
        String fotografoNome = null;
        BigDecimal custoDeslocamento = null;
        Boolean repassarDeslocamento = null;
        BigDecimal percentualEntrada = null;
        UUID id = null;
        LocalDateTime dataHoraEnsaio = null;
        Integer duracaoMinutos = null;
        String localEnsaio = null;
        String enderecoCompleto = null;
        BigDecimal valorTotal = null;
        BigDecimal valorEntradaExigido = null;
        BigDecimal valorEntradaPago = null;
        BigDecimal valorRestante = null;
        BigDecimal valorExtras = null;
        BigDecimal taxaDeslocamento = null;
        BigDecimal valorTotalFinal = null;
        LocalDateTime dataConfirmacao = null;
        LocalDateTime dataRealizacao = null;
        LocalDateTime dataEnvioSelecao = null;
        LocalDateTime dataEntregaFinal = null;
        LocalDateTime dataFinalizacao = null;
        String urlComprovanteEntrada = null;
        String urlComprovanteFinal = null;
        Boolean autorizaUsoImagem = null;
        String clausulasPersonalizadas = null;
        Boolean contratoGerado = null;
        Boolean ensaioDestaque = null;
        String observacoes = null;
        UUID tokenGaleria = null;
        BigDecimal valorPartilhaGlobal = null;
        BigDecimal valorLucroCrm = null;
        if ( agendamento != null ) {
            clienteId = agendamentoClienteId( agendamento );
            clienteNome = agendamentoClienteNome( agendamento );
            clienteTelefone = agendamentoClienteTelefone( agendamento );
            clienteEmail = agendamentoClienteEmail( agendamento );
            clienteCpf = agendamentoClienteCpf( agendamento );
            clienteCidade = agendamentoClienteCidade( agendamento );
            clienteEstado = agendamentoClienteEstado( agendamento );
            pacoteId = agendamentoPacoteId( agendamento );
            pacoteNome = agendamentoPacoteNome( agendamento );
            editorId = agendamentoEditorId( agendamento );
            editorNome = agendamentoEditorNome( agendamento );
            fotografoId = agendamentoFotografoId( agendamento );
            fotografoNome = agendamentoFotografoNome( agendamento );
            if ( agendamento.getCustoDeslocamento() != null ) {
                custoDeslocamento = agendamento.getCustoDeslocamento();
            }
            else {
                custoDeslocamento = new BigDecimal( "0" );
            }
            if ( agendamento.getRepassarDeslocamento() != null ) {
                repassarDeslocamento = agendamento.getRepassarDeslocamento();
            }
            else {
                repassarDeslocamento = true;
            }
            if ( agendamento.getPercentualEntrada() != null ) {
                percentualEntrada = agendamento.getPercentualEntrada();
            }
            else {
                percentualEntrada = new BigDecimal( "30" );
            }
            id = agendamento.getId();
            dataHoraEnsaio = agendamento.getDataHoraEnsaio();
            duracaoMinutos = agendamento.getDuracaoMinutos();
            localEnsaio = agendamento.getLocalEnsaio();
            enderecoCompleto = agendamento.getEnderecoCompleto();
            valorTotal = agendamento.getValorTotal();
            valorEntradaExigido = agendamento.getValorEntradaExigido();
            valorEntradaPago = agendamento.getValorEntradaPago();
            valorRestante = agendamento.getValorRestante();
            valorExtras = agendamento.getValorExtras();
            taxaDeslocamento = agendamento.getTaxaDeslocamento();
            valorTotalFinal = agendamento.getValorTotalFinal();
            dataConfirmacao = agendamento.getDataConfirmacao();
            dataRealizacao = agendamento.getDataRealizacao();
            dataEnvioSelecao = agendamento.getDataEnvioSelecao();
            dataEntregaFinal = agendamento.getDataEntregaFinal();
            dataFinalizacao = agendamento.getDataFinalizacao();
            urlComprovanteEntrada = agendamento.getUrlComprovanteEntrada();
            urlComprovanteFinal = agendamento.getUrlComprovanteFinal();
            autorizaUsoImagem = agendamento.getAutorizaUsoImagem();
            clausulasPersonalizadas = agendamento.getClausulasPersonalizadas();
            contratoGerado = agendamento.getContratoGerado();
            ensaioDestaque = agendamento.getEnsaioDestaque();
            observacoes = agendamento.getObservacoes();
            tokenGaleria = agendamento.getTokenGaleria();
            valorPartilhaGlobal = agendamento.getValorPartilhaGlobal();
            valorLucroCrm = agendamento.getValorLucroCrm();
        }
        List<AgendamentoResponse.FotografoNoAgendamento> fotografos1 = null;
        fotografos1 = agendamentoFotografoListToFotografoNoAgendamentoList( fotografos );
        BigDecimal valorComissao1 = null;
        valorComissao1 = valorComissao;
        String indicadorNome1 = null;
        indicadorNome1 = indicadorNome;
        String statusComissao1 = null;
        statusComissao1 = statusComissao;

        BigDecimal valorPacote = agendamento.getValorTotal().subtract(agendamento.getTaxaDeslocamento());
        BigDecimal saldoDevedor = agendamento.getValorTotalFinal().subtract(agendamento.getValorEntradaPago());
        String status = agendamento.getStatus().name();
        LocalDateTime createdAt = null;
        LocalDateTime updatedAt = null;

        AgendamentoResponse agendamentoResponse = new AgendamentoResponse( id, clienteId, clienteNome, clienteTelefone, clienteEmail, clienteCpf, clienteCidade, clienteEstado, pacoteId, pacoteNome, editorId, editorNome, fotografoId, fotografoNome, dataHoraEnsaio, duracaoMinutos, localEnsaio, enderecoCompleto, valorTotal, valorEntradaExigido, valorEntradaPago, valorRestante, valorExtras, taxaDeslocamento, custoDeslocamento, repassarDeslocamento, valorTotalFinal, percentualEntrada, valorPacote, saldoDevedor, status, dataConfirmacao, dataRealizacao, dataEnvioSelecao, dataEntregaFinal, dataFinalizacao, urlComprovanteEntrada, urlComprovanteFinal, autorizaUsoImagem, clausulasPersonalizadas, contratoGerado, ensaioDestaque, observacoes, tokenGaleria, createdAt, updatedAt, fotografos1, valorPartilhaGlobal, valorLucroCrm, valorComissao1, indicadorNome1, statusComissao1 );

        return agendamentoResponse;
    }

    @Override
    public AgendamentoResponse.FotografoNoAgendamento toFotografoNoAgendamento(AgendamentoFotografo fotografos) {
        if ( fotografos == null ) {
            return null;
        }

        UUID fotografoId = null;
        String fotografoNome = null;
        TipoRepasse tipoValor = null;
        BigDecimal valorRepassar = null;
        RepasseStatus status = null;
        LocalDateTime dataPagamento = null;
        BigDecimal percentual = null;
        Papel papelParceiro = null;

        fotografoId = fotografosFotografoId( fotografos );
        fotografoNome = fotografosFotografoNome( fotografos );
        if ( fotografos.getTipoValor() != null ) {
            tipoValor = fotografos.getTipoValor();
        }
        else {
            tipoValor = TipoRepasse.FIXO;
        }
        valorRepassar = fotografos.getValorRepassar();
        status = fotografos.getStatus();
        dataPagamento = fotografos.getDataPagamento();
        percentual = fotografos.getPercentual();
        papelParceiro = fotografos.getPapelParceiro();

        AgendamentoResponse.FotografoNoAgendamento fotografoNoAgendamento = new AgendamentoResponse.FotografoNoAgendamento( fotografoId, fotografoNome, valorRepassar, status, dataPagamento, tipoValor, percentual, papelParceiro );

        return fotografoNoAgendamento;
    }

    private UUID agendamentoClienteId(Agendamento agendamento) {
        Cliente cliente = agendamento.getCliente();
        if ( cliente == null ) {
            return null;
        }
        return cliente.getId();
    }

    private String agendamentoClienteNome(Agendamento agendamento) {
        Cliente cliente = agendamento.getCliente();
        if ( cliente == null ) {
            return null;
        }
        return cliente.getNome();
    }

    private String agendamentoClienteTelefone(Agendamento agendamento) {
        Cliente cliente = agendamento.getCliente();
        if ( cliente == null ) {
            return null;
        }
        return cliente.getTelefone();
    }

    private String agendamentoClienteEmail(Agendamento agendamento) {
        Cliente cliente = agendamento.getCliente();
        if ( cliente == null ) {
            return null;
        }
        return cliente.getEmail();
    }

    private String agendamentoClienteCpf(Agendamento agendamento) {
        Cliente cliente = agendamento.getCliente();
        if ( cliente == null ) {
            return null;
        }
        return cliente.getCpf();
    }

    private String agendamentoClienteCidade(Agendamento agendamento) {
        Cliente cliente = agendamento.getCliente();
        if ( cliente == null ) {
            return null;
        }
        return cliente.getCidade();
    }

    private String agendamentoClienteEstado(Agendamento agendamento) {
        Cliente cliente = agendamento.getCliente();
        if ( cliente == null ) {
            return null;
        }
        return cliente.getEstado();
    }

    private UUID agendamentoPacoteId(Agendamento agendamento) {
        Pacote pacote = agendamento.getPacote();
        if ( pacote == null ) {
            return null;
        }
        return pacote.getId();
    }

    private String agendamentoPacoteNome(Agendamento agendamento) {
        Pacote pacote = agendamento.getPacote();
        if ( pacote == null ) {
            return null;
        }
        return pacote.getNome();
    }

    private UUID agendamentoEditorId(Agendamento agendamento) {
        User editor = agendamento.getEditor();
        if ( editor == null ) {
            return null;
        }
        return editor.getId();
    }

    private String agendamentoEditorNome(Agendamento agendamento) {
        User editor = agendamento.getEditor();
        if ( editor == null ) {
            return null;
        }
        return editor.getNome();
    }

    private UUID agendamentoFotografoId(Agendamento agendamento) {
        User fotografo = agendamento.getFotografo();
        if ( fotografo == null ) {
            return null;
        }
        return fotografo.getId();
    }

    private String agendamentoFotografoNome(Agendamento agendamento) {
        User fotografo = agendamento.getFotografo();
        if ( fotografo == null ) {
            return null;
        }
        return fotografo.getNome();
    }

    protected List<AgendamentoResponse.FotografoNoAgendamento> agendamentoFotografoListToFotografoNoAgendamentoList(List<AgendamentoFotografo> list) {
        if ( list == null ) {
            return null;
        }

        List<AgendamentoResponse.FotografoNoAgendamento> list1 = new ArrayList<AgendamentoResponse.FotografoNoAgendamento>( list.size() );
        for ( AgendamentoFotografo agendamentoFotografo : list ) {
            list1.add( toFotografoNoAgendamento( agendamentoFotografo ) );
        }

        return list1;
    }

    private UUID fotografosFotografoId(AgendamentoFotografo agendamentoFotografo) {
        User fotografo = agendamentoFotografo.getFotografo();
        if ( fotografo == null ) {
            return null;
        }
        return fotografo.getId();
    }

    private String fotografosFotografoNome(AgendamentoFotografo agendamentoFotografo) {
        User fotografo = agendamentoFotografo.getFotografo();
        if ( fotografo == null ) {
            return null;
        }
        return fotografo.getNome();
    }
}
