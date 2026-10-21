package com.photoizer.crm.agenda.security

import com.photoizer.crm.agenda.api.AgendamentoResponse
import com.photoizer.crm.agenda.model.RepasseStatus
import com.photoizer.crm.auth.model.Papel
import com.photoizer.crm.shared.model.FormaPagamento
import com.photoizer.crm.shared.model.TipoRepasse
import spock.lang.Specification

import java.time.LocalDateTime

class AgendamentoResponseRedacaoSpec extends Specification {

    def "oculta métricas internas e preserva valores do cliente"() {
        given: "uma resposta com parceiro e métricas internas"
        def fotografo = new AgendamentoResponse.FotografoNoAgendamento(
            UUID.randomUUID(), "Fotógrafo", new BigDecimal("500.00"), RepasseStatus.PENDENTE,
            null, TipoRepasse.FIXO, null, Papel.FOTOGRAFO)
        def original = novoResponse([fotografo])

        when: "a resposta é redigida para papel sem visibilidade financeira"
        def redigido = original.ocultarDadosInternos()

        then: "os valores de atendimento ao cliente permanecem"
        redigido.valorTotal() == original.valorTotal()
        redigido.valorPacote() == original.valorPacote()
        redigido.valorEntradaExigido() == original.valorEntradaExigido()
        redigido.saldoDevedor() == original.saldoDevedor()

        and: "as métricas internas são removidas"
        redigido.custoDeslocamento() == null
        redigido.valorPartilhaGlobal() == null
        redigido.valorLucroCrm() == null
        redigido.valorComissao() == null
        redigido.indicadorNome() == null
        redigido.statusComissao() == null

        and: "o parceiro continua visível, mas sem repasse"
        redigido.fotografos().size() == 1
        def f = redigido.fotografos().first()
        f.valorRepassar() == null
        f.tipoValor() == null
        f.percentual() == null
        f.fotografoId() == fotografo.fotografoId()
        f.fotografoNome() == "Fotógrafo"
    }

    def "oculta métricas mesmo sem parceiros vinculados"() {
        given: "uma resposta sem parceiros"
        def original = novoResponse(null)

        when: "a resposta é redigida"
        def redigido = original.ocultarDadosInternos()

        then: "as métricas internas são removidas e os valores do cliente permanecem"
        redigido.custoDeslocamento() == null
        redigido.valorLucroCrm() == null
        redigido.valorComissao() == null
        redigido.fotografos() == null
        redigido.valorTotal() != null
    }

    private AgendamentoResponse novoResponse(List<AgendamentoResponse.FotografoNoAgendamento> fotografos) {
        new AgendamentoResponse(
            UUID.randomUUID(), UUID.randomUUID(), "Cliente", "11999999999", "c@x.com", null,
            "São Paulo", "SP", UUID.randomUUID(), "Pacote", "#64748B", null, null,
            null, null, LocalDateTime.now(), 60, "Estúdio",
            new BigDecimal("1000.00"), new BigDecimal("300.00"), new BigDecimal("300.00"),
            new BigDecimal("700.00"), BigDecimal.ZERO, new BigDecimal("50.00"),
            new BigDecimal("40.00"), true, new BigDecimal("1000.00"), new BigDecimal("30"),
            new BigDecimal("800.00"), new BigDecimal("700.00"), "CONFIRMADO", "PROPOSTA",
            null, null, null, null, false, false, FormaPagamento.DINHEIRO,
            true, null, "token", null, null, false, false, null, null,
            null, null, null, LocalDateTime.now(), LocalDateTime.now(),
            fotografos, new BigDecimal("400.00"), new BigDecimal("200.00"), new BigDecimal("150.00"),
            "Indicador X", "PENDENTE")
    }
}
