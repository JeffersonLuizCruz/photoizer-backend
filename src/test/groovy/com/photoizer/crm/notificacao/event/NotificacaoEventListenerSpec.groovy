package com.photoizer.crm.notificacao.event

import com.photoizer.crm.agenda.event.EntregaPendenteEvent
import com.photoizer.crm.notificacao.model.TipoNotificacao
import com.photoizer.crm.notificacao.service.NotificacaoService
import com.photoizer.crm.shared.port.UsuarioStaffPort
import spock.lang.Specification
import spock.lang.Subject

import java.time.LocalDateTime

class NotificacaoEventListenerSpec extends Specification {

    def notificacaoService = Mock(NotificacaoService)
    def usuarioStaffPort = Mock(UsuarioStaffPort)

    @Subject
    def listener = new NotificacaoEventListener(notificacaoService, usuarioStaffPort)

    def "cria entrega pendente para cada staff que ainda não recebeu o lembrete no dia"() {
        given:
        def admin = UUID.randomUUID()
        def editor = UUID.randomUUID()
        def agendamentoId = UUID.randomUUID()
        def event = new EntregaPendenteEvent(
            agendamentoId, "Maria Souza", "Pacote Ensaio", LocalDateTime.now(), true)
        usuarioStaffPort.listarStaffAtivos() >> [admin, editor]
        def link = "/agenda/${agendamentoId}"
        notificacaoService.existeNoDia(admin, TipoNotificacao.ENTREGA_PENDENTE, link, _ as LocalDateTime) >> false
        notificacaoService.existeNoDia(editor, TipoNotificacao.ENTREGA_PENDENTE, link, _ as LocalDateTime) >> true

        when:
        listener.onEntregaPendente(event)

        then:
        1 * notificacaoService.criar(
            admin,
            "Lembrete de entrega pendente",
            { it.contains("Maria Souza") && it.contains("Pacote Ensaio") },
            link,
            TipoNotificacao.ENTREGA_PENDENTE)
        0 * notificacaoService.criar(editor, _, _, _, _)
    }

    def "usa título de nova entrega quando o evento não é do job diário"() {
        given:
        def admin = UUID.randomUUID()
        def event = new EntregaPendenteEvent(
            UUID.randomUUID(), "Maria Souza", "Pacote Ensaio", null, false)
        usuarioStaffPort.listarStaffAtivos() >> [admin]
        notificacaoService.existeNoDia(_, _, _, _ as LocalDateTime) >> false

        when:
        listener.onEntregaPendente(event)

        then:
        1 * notificacaoService.criar(
            admin,
            "Entrega pendente",
            { it.contains("Maria Souza") },
            _ as String,
            TipoNotificacao.ENTREGA_PENDENTE)
    }

    def "não cria notificações quando não há staff ativo"() {
        given:
        usuarioStaffPort.listarStaffAtivos() >> []

        when:
        listener.onEntregaPendente(new EntregaPendenteEvent(
            UUID.randomUUID(), "Maria Souza", "Pacote Ensaio", null, true))

        then:
        0 * notificacaoService.criar(_, _, _, _, _)
    }
}
