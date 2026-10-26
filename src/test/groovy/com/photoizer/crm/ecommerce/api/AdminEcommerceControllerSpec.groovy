package com.photoizer.crm.ecommerce.api

import com.photoizer.crm.agenda.model.Agendamento
import com.photoizer.crm.pacote.model.Pacote
import com.photoizer.crm.ecommerce.service.CompraQueryService
import com.photoizer.crm.ecommerce.service.EcommerceService
import com.photoizer.crm.ecommerce.service.FavoritoService
import com.photoizer.crm.ecommerce.service.GaleriaQueryService
import com.photoizer.crm.ecommerce.service.PagamentoExtraService
import com.photoizer.crm.foto.api.FotoMapper
import spock.lang.Specification
import spock.lang.Subject

class AdminEcommerceControllerSpec extends Specification {

    def ecommerceService = Mock(EcommerceService)
    def galeriaQueryService = Mock(GaleriaQueryService)
    def compraQueryService = Mock(CompraQueryService)
    def ecommerceMapper = Mock(EcommerceMapper)
    def fotoMapper = Mock(FotoMapper)
    def pagamentoExtraService = Mock(PagamentoExtraService)
    def favoritoService = Mock(FavoritoService)

    @Subject
    def controller = new AdminEcommerceController(
        ecommerceService,
        galeriaQueryService,
        compraQueryService,
        ecommerceMapper,
        fotoMapper,
        pagamentoExtraService,
        favoritoService,
    )

    def "resumo preenche o token e o link relativo da galeria"() {
        given:
        def agendamentoId = UUID.randomUUID()
        def tokenGaleria = UUID.randomUUID()
        def agendamento = Agendamento.builder()
            .id(agendamentoId)
            .tokenGaleria(tokenGaleria)
            .pacote(Pacote.builder().quantidadeFotos(12).build())
            .build()

        galeriaQueryService.buscarAgendamentoPorId(agendamentoId) >> agendamento
        galeriaQueryService.listarFotosPorAgendamento(agendamentoId) >> []
        compraQueryService.listarComprasPorAgendamento(agendamentoId) >> []
        favoritoService.contarFavoritosPorFoto(agendamentoId) >> [:]

        when:
        def resposta = controller.resumo(agendamentoId).body

        then:
        resposta.tokenGaleria() == tokenGaleria
        resposta.linkGaleria() == "/g/" + tokenGaleria
        resposta.pacoteQuantidadeFotos() == 12
    }

    def "resumo nao inventa link quando o agendamento nao possui token"() {
        given:
        def agendamentoId = UUID.randomUUID()
        def agendamento = Agendamento.builder()
            .id(agendamentoId)
            .tokenGaleria(null)
            .build()

        galeriaQueryService.buscarAgendamentoPorId(agendamentoId) >> agendamento
        galeriaQueryService.listarFotosPorAgendamento(agendamentoId) >> []
        compraQueryService.listarComprasPorAgendamento(agendamentoId) >> []
        favoritoService.contarFavoritosPorFoto(agendamentoId) >> [:]

        when:
        def resposta = controller.resumo(agendamentoId).body

        then:
        resposta.tokenGaleria() == null
        resposta.linkGaleria() == null
    }
}
