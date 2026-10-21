package com.photoizer.crm.agenda.model

import com.photoizer.crm.agenda.exception.StatusAgendamentoInvalidoException
import spock.lang.Specification
import spock.lang.Unroll

class StatusAgendamentoSpec extends Specification {

    @Unroll
    def "permite a transição de #origem para #destino"() {
        expect: "a transição é considerada válida"
        StatusAgendamento.valueOf(origem).podeTransicionarPara(StatusAgendamento.valueOf(destino))

        where:
        origem                  | destino
        "PRE_RESERVA"           | "AGUARDANDO_APROVACAO"
        "PRE_RESERVA"           | "CANCELADO"
        "AGUARDANDO_APROVACAO"  | "PAGAMENTO_CONFIRMADO"
        "AGUARDANDO_APROVACAO"  | "CANCELADO"
        "PAGAMENTO_CONFIRMADO"  | "CONFIRMADO"
        "PAGAMENTO_CONFIRMADO"  | "CANCELADO"
        "CONFIRMADO"            | "REALIZADO"
        "CONFIRMADO"            | "CANCELADO"
        "CONFIRMADO"            | "NO_SHOW"
        "REALIZADO"             | "AGUARDANDO_PAGAMENTO_FINAL"
        "REALIZADO"             | "EM_EDICAO"
        "AGUARDANDO_PAGAMENTO_FINAL" | "EM_EDICAO"
        "EM_EDICAO"             | "FINALIZADO"
    }

    @Unroll
    def "bloqueia a transição inválida de #origem para #destino"() {
        when: "uma transição fora do fluxo é solicitada"
        StatusAgendamento.valueOf(origem).validarTransicao(StatusAgendamento.valueOf(destino))

        then: "uma exceção de status inválido é lançada"
        thrown(StatusAgendamentoInvalidoException)

        where:
        origem         | destino
        "FINALIZADO"   | "CONFIRMADO"
        "CANCELADO"    | "PRE_RESERVA"
        "NO_SHOW"      | "CONFIRMADO"
        "PRE_RESERVA"  | "CONFIRMADO"
        "EM_EDICAO"    | "REALIZADO"
    }

    @Unroll
    def "status #status participa da pré-reserva? #preReserva"() {
        expect: "o status é classificado corretamente"
        StatusAgendamento.valueOf(status).isPreReserva() == preReserva

        where:
        status                 | preReserva
        "PRE_RESERVA"          | true
        "AGUARDANDO_APROVACAO" | true
        "PAGAMENTO_CONFIRMADO" | true
        "CONFIRMADO"           | false
        "CANCELADO"            | false
    }
}
