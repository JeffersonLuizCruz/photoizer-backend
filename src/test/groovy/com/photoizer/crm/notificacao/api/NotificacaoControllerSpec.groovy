package com.photoizer.crm.notificacao.api

import com.photoizer.crm.notificacao.model.Notificacao
import com.photoizer.crm.notificacao.model.TipoNotificacao
import com.photoizer.crm.notificacao.service.NotificacaoService
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import spock.lang.Specification
import spock.lang.Subject

class NotificacaoControllerSpec extends Specification {

    def service = Mock(NotificacaoService)
    def mapper = Mock(NotificacaoMapper)

    @Subject
    def controller = new NotificacaoController(service, mapper)

    def "retorna PageResponse e converte a pagina 1-based para o Pageable"() {
        given:
        def userId = UUID.randomUUID()
        def notificacao = new Notificacao(userId, "Novo ensaio", "Ensaio confirmado", null, TipoNotificacao.NOVO_ENSAIO)
        def response = new NotificacaoResponse(
            notificacao.id, notificacao.titulo, notificacao.mensagem,
            notificacao.link, notificacao.tipo, notificacao.lida, notificacao.createdAt)
        PageRequest pageableRecebido = null
        service.listar(userId, _ as PageRequest) >> { args ->
            pageableRecebido = args[1] as PageRequest
            return new PageImpl<Notificacao>([notificacao], pageableRecebido, 1)
        }
        mapper.toResponse(notificacao) >> response

        when:
        def resultado = controller.listar(userId.toString(), 1, 50)

        then:
        resultado.body.data == [response]
        resultado.body.page == 1
        resultado.body.perPage == 50
        resultado.body.total == 1
        resultado.body.totalPages == 1
        pageableRecebido.pageNumber == 0
        pageableRecebido.pageSize == 50
    }
}
