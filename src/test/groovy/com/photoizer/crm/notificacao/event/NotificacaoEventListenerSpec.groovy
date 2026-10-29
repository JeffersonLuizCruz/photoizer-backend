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

    def "notifica fotógrafo e staff quando o cliente solicita compra de extras"() {
        given:
        def agendamentoId = UUID.randomUUID()
        def fotografo = UUID.randomUUID()
        def admin = UUID.randomUUID()
        def event = new com.photoizer.crm.ecommerce.event.CompraExtraSolicitadaEvent(
            agendamentoId, UUID.randomUUID(), "Maria Souza", fotografo, 3,
            new BigDecimal("45.00"))
        usuarioStaffPort.listarAprovadoresCompras() >> [admin]

        when:
        listener.onCompraExtraSolicitada(event)

        then:
        1 * notificacaoService.criar(
            fotografo,
            "Nova solicitação de compra de extra",
            { it.contains("Maria Souza") && it.contains("3 foto") && it.contains('R$ 45,00') },
            "/agenda/${agendamentoId}/fotos?tab=compras",
            TipoNotificacao.COMPRA_EXTRA_SOLICITADA)
        1 * notificacaoService.criar(
            admin,
            "Nova solicitação de compra de extra",
            _,
            _,
            TipoNotificacao.COMPRA_EXTRA_SOLICITADA)
    }

    def "notifica o staff quando o comprovante da compra de extra é enviado"() {
        given:
        def agendamentoId = UUID.randomUUID()
        def admin = UUID.randomUUID()
        def event = new com.photoizer.crm.ecommerce.event.ComprovanteCompraEnviadoEvent(
            agendamentoId, UUID.randomUUID(), "Maria Souza", null, 2,
            new BigDecimal("30.00"))
        usuarioStaffPort.listarAprovadoresCompras() >> [admin]

        when:
        listener.onComprovanteCompraEnviado(event)

        then:
        1 * notificacaoService.criar(
            admin,
            "Comprovante de compra de extra enviado",
            { it.contains('R$ 30,00') && it.contains("Aprove a compra") },
            "/agenda/${agendamentoId}/fotos?tab=compras",
            TipoNotificacao.COMPRA_EXTRA_COMPROVANTE_ENVIADO)
    }

    def "não duplica a notificação quando o fotógrafo também é staff ativo"() {
        given:
        def agendamentoId = UUID.randomUUID()
        def fotografo = UUID.randomUUID()
        def event = new com.photoizer.crm.ecommerce.event.CompraExtraSolicitadaEvent(
            agendamentoId, UUID.randomUUID(), "Maria Souza", fotografo, 1,
            new BigDecimal("15.00"))
        usuarioStaffPort.listarAprovadoresCompras() >> [fotografo]

        when:
        listener.onCompraExtraSolicitada(event)

        then:
        1 * notificacaoService.criar(fotografo, _, _, _, TipoNotificacao.COMPRA_EXTRA_SOLICITADA)
    }
}
