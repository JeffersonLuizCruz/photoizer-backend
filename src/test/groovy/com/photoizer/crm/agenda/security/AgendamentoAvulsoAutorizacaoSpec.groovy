package com.photoizer.crm.agenda.security

import com.photoizer.crm.agenda.api.AgendamentoAvulsoController
import com.photoizer.crm.agenda.api.AtualizarAgendamentoAvulsoRequest
import jakarta.annotation.security.RolesAllowed
import spock.lang.Specification

import java.util.UUID

class AgendamentoAvulsoAutorizacaoSpec extends Specification {

    def "agendamento avulso exige ADMIN/AGENDADOR e não aceita FOTOGRAFO"() {
        given: "a anotação de papéis do controller"
        def roles = AgendamentoAvulsoController.getAnnotation(RolesAllowed)

        expect: "somente ADMIN e AGENDADOR são permitidos"
        roles != null
        roles.value().toList().sort() == ["ADMIN", "AGENDADOR"]
    }

    def "correção do avulso herda a restrição ADMIN/AGENDADOR"() {
        given: "o método de atualização do avulso"
        def method = AgendamentoAvulsoController.getMethod(
            "atualizar", UUID, AtualizarAgendamentoAvulsoRequest)

        expect: "o endpoint existe e só ADMIN/AGENDADOR podem acessá-lo"
        method != null
        AgendamentoAvulsoController.getAnnotation(RolesAllowed).value().toList().sort() == ["ADMIN", "AGENDADOR"]
    }
}
