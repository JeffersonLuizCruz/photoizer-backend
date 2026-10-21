package com.photoizer.crm.agenda.security

import com.photoizer.crm.agenda.api.AgendamentoAvulsoController
import jakarta.annotation.security.RolesAllowed
import spock.lang.Specification

class AgendamentoAvulsoAutorizacaoSpec extends Specification {

    def "agendamento avulso exige ADMIN/AGENDADOR e não aceita FOTOGRAFO"() {
        given: "a anotação de papéis do controller"
        def roles = AgendamentoAvulsoController.getAnnotation(RolesAllowed)

        expect: "somente ADMIN e AGENDADOR são permitidos"
        roles != null
        roles.value().toList().sort() == ["ADMIN", "AGENDADOR"]
    }
}
