package com.photoizer.crm.ecommerce.service

import com.photoizer.crm.ecommerce.model.CompraExtra
import com.photoizer.crm.ecommerce.model.ConfiguracaoPagamentoEcommerce
import com.photoizer.crm.ecommerce.model.StatusCompraExtra
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository
import com.photoizer.crm.ecommerce.repository.ConfiguracaoPagamentoEcommerceRepository
import com.photoizer.crm.shared.exception.BadRequestException
import com.photoizer.crm.shared.exception.ConflictException
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class EcommercePagamentoSpec extends Specification {

    def configuracaoRepository = Mock(ConfiguracaoPagamentoEcommerceRepository)
    def compraRepository = Mock(CompraExtraRepository)
    def galeriaQueryService = Mock(GaleriaQueryService)
    def eventPublisher = Mock(ApplicationEventPublisher)

    @Subject
    def configuracaoService = new ConfiguracaoPagamentoEcommerceService(configuracaoRepository)

    @Subject
    def pagamentoService = new PagamentoExtraService(
        compraRepository, galeriaQueryService, eventPublisher)

    def "retorna configuracao vazia quando ainda nao existe PIX"() {
        given:
        configuracaoRepository.findById(ConfiguracaoPagamentoEcommerce.ID_UNICO) >> Optional.empty()

        when:
        def configuracao = configuracaoService.obter()

        then:
        configuracao.pixChave == null
        configuracao.pixTipoChave == null
    }

    def "atualiza PIX normalizando espacos e registrando o usuario"() {
        given:
        def existente = new ConfiguracaoPagamentoEcommerce()
        configuracaoRepository.findById(ConfiguracaoPagamentoEcommerce.ID_UNICO) >> Optional.of(existente)
        configuracaoRepository.save(_ as ConfiguracaoPagamentoEcommerce) >> { ConfiguracaoPagamentoEcommerce c -> c }

        when:
        def atualizada = configuracaoService.atualizar("  pix@studio.com  ", "  EMAIL ")

        then:
        atualizada.pixChave == "pix@studio.com"
        atualizada.pixTipoChave == "EMAIL"
    }

    def "define link de cartao em compra pendente"() {
        given:
        def compra = CompraExtra.builder()
            .id(UUID.randomUUID())
            .status(StatusCompraExtra.AGUARDANDO_COMPROVANTE)
            .build()
        compraRepository.findById(compra.id) >> Optional.of(compra)
        compraRepository.save(_ as CompraExtra) >> { CompraExtra c -> c }

        when:
        def atualizada = pagamentoService.atualizarLinkPagamentoCartao(
            compra.id, " https://pagamento.exemplo.com/abc ")

        then:
        atualizada.linkPagamentoCartao == "https://pagamento.exemplo.com/abc"
    }

    def "remove link de cartao quando valor e vazio"() {
        given:
        def compra = CompraExtra.builder()
            .id(UUID.randomUUID())
            .status(StatusCompraExtra.AGUARDANDO_CONFIRMACAO)
            .linkPagamentoCartao("https://pagamento.exemplo.com/abc")
            .build()
        compraRepository.findById(compra.id) >> Optional.of(compra)
        compraRepository.save(_ as CompraExtra) >> { CompraExtra c -> c }

        when:
        def atualizada = pagamentoService.atualizarLinkPagamentoCartao(compra.id, "   ")

        then:
        atualizada.linkPagamentoCartao == null
    }

    @Unroll
    def "rejeita link invalido '#link'"() {
        given:
        def compra = CompraExtra.builder()
            .id(UUID.randomUUID())
            .status(StatusCompraExtra.AGUARDANDO_COMPROVANTE)
            .build()
        compraRepository.findById(compra.id) >> Optional.of(compra)

        when:
        pagamentoService.atualizarLinkPagamentoCartao(compra.id, link)

        then:
        thrown(BadRequestException)

        where:
        link << ["ftp://arquivo", "javascript:alert(1)", "sem-url", "https://"]
    }

    @Unroll
    def "nao permite link em compra com status #status"() {
        given:
        def compra = CompraExtra.builder()
            .id(UUID.randomUUID())
            .status(status)
            .build()
        compraRepository.findById(compra.id) >> Optional.of(compra)

        when:
        pagamentoService.atualizarLinkPagamentoCartao(compra.id, "https://pagamento.exemplo.com/abc")

        then:
        thrown(ConflictException)

        where:
        status << [StatusCompraExtra.PAGA, StatusCompraExtra.CANCELADA]
    }
}
