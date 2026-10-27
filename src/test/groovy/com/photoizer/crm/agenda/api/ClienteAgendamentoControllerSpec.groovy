package com.photoizer.crm.agenda.api

import jakarta.annotation.security.RolesAllowed
import org.springframework.web.bind.annotation.RequestMapping
import spock.lang.Specification

class ClienteAgendamentoControllerSpec extends Specification {

    def "expõe a rota de ensaios do cliente protegida pelo papel CLIENTE"() {
        expect:
        ClienteAgendamentoController.getAnnotation(RequestMapping)
            .value()
            .toList() == ["/api/v1/auth/cliente/agendamentos"]

        and:
        ClienteAgendamentoController.getAnnotation(RolesAllowed)
            .value()
            .toList() == ["CLIENTE"]
    }
}
