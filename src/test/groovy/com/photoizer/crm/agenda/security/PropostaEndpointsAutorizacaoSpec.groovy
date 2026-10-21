package com.photoizer.crm.agenda.security

import com.photoizer.crm.agenda.api.AgendamentoController
import jakarta.annotation.security.RolesAllowed
import spock.lang.Specification
import spock.lang.Unroll

class PropostaEndpointsAutorizacaoSpec extends Specification {

    @Unroll
    def "endpoint #nomeMetodo exige ADMIN/FOTOGRAFO"() {
        given: "o método do controller de agendamento"
        def metodo = AgendamentoController.methods.find { it.name == nomeMetodo }

        expect: "o método existe e declara os papéis esperados"
        metodo != null
        metodo.getAnnotation(RolesAllowed)?.value()?.toList()?.sort() == ["ADMIN", "FOTOGRAFO"]

        where:
        nomeMetodo << ["atualizarProposta", "removerProposta"]
    }
}
