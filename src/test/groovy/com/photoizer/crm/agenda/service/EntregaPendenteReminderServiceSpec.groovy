package com.photoizer.crm.agenda.service

import com.photoizer.crm.agenda.event.EntregaPendenteEvent
import com.photoizer.crm.agenda.model.StatusAgendamento
import com.photoizer.crm.agenda.repository.AgendamentoRepository
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.Subject

import static com.photoizer.crm.agenda.support.AgendaFixtures.agendamento
import static com.photoizer.crm.agenda.support.AgendaFixtures.cliente

class EntregaPendenteReminderServiceSpec extends Specification {

    def agendamentoRepository = Mock(AgendamentoRepository)
    def eventPublisher = Mock(ApplicationEventPublisher)

    @Subject
    def service = new EntregaPendenteReminderService(agendamentoRepository, eventPublisher)

    def "publica um lembrete diário por agendamento em edição"() {
        given: "dois agendamentos com entrega pendente"
        def primeiro = agendamento(
            status: StatusAgendamento.EM_EDICAO,
            cliente: cliente(nome: "Maria Souza"))
        def segundo = agendamento(
            status: StatusAgendamento.EM_EDICAO,
            cliente: cliente(nome: "João Lima"))
        agendamentoRepository.findByStatus(StatusAgendamento.EM_EDICAO) >> [primeiro, segundo]
        def eventos = []

        when: "o job diário é executado"
        service.lembrarEntregasPendentes()

        then: "um evento de lembrete é publicado para cada entrega"
        2 * eventPublisher.publishEvent(_ as EntregaPendenteEvent) >> { EntregaPendenteEvent e ->
            eventos << e
        }
        eventos*.agendamentoId as Set == [primeiro.id, segundo.id] as Set
        eventos*.clienteNome as Set == ["Maria Souza", "João Lima"] as Set
        eventos.every { it.lembreteDiario }
    }

    def "não publica eventos quando não há entregas pendentes"() {
        given:
        agendamentoRepository.findByStatus(StatusAgendamento.EM_EDICAO) >> []

        when:
        service.lembrarEntregasPendentes()

        then:
        0 * eventPublisher.publishEvent(_)
    }
}
