package com.photoizer.crm.ecommerce.service

import com.photoizer.crm.ecommerce.api.LeadPacoteRequest
import com.photoizer.crm.ecommerce.event.LeadPacoteCriadoEvent
import com.photoizer.crm.ecommerce.model.LeadPacote
import com.photoizer.crm.ecommerce.model.StatusLead
import com.photoizer.crm.ecommerce.repository.LeadPacoteRepository
import com.photoizer.crm.pacote.exception.PacoteInativoException
import com.photoizer.crm.pacote.model.Pacote
import com.photoizer.crm.pacote.service.PacoteQueryService
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.Subject

class LeadPacoteServiceSpec extends Specification {

    def leadRepository = Mock(LeadPacoteRepository)
    def pacoteQueryService = Mock(PacoteQueryService)
    def eventPublisher = Mock(ApplicationEventPublisher)
    def pacoteId = UUID.randomUUID()

    @Subject
    def service = new LeadPacoteService(leadRepository, pacoteQueryService, eventPublisher)

    def "cria lead novo a partir de pacote ativo e notifica o comercial"() {
        given:
        pacoteQueryService.buscarEntityPorId(pacoteId) >> pacote(true)
        leadRepository.save(_ as LeadPacote) >> { LeadPacote lead ->
            lead.id = UUID.randomUUID()
            lead
        }

        when:
        def lead = service.criar(request())

        then:
        lead.status == StatusLead.NOVO
        lead.pacoteNome == 'Ensaio'
        lead.quantidadeExtras == 2
        1 * eventPublisher.publishEvent({ it instanceof LeadPacoteCriadoEvent
            && it.pacoteNome() == 'Ensaio'
            && it.clienteNome() == 'Maria' })
    }

    def "rejeita lead de pacote inativo"() {
        given:
        pacoteQueryService.buscarEntityPorId(pacoteId) >> pacote(false)

        when:
        service.criar(request())

        then:
        thrown(PacoteInativoException)
        0 * leadRepository.save(_)
    }

    def "atualiza o status comercial do lead"() {
        given:
        def lead = LeadPacote.builder()
            .id(UUID.randomUUID())
            .status(StatusLead.NOVO)
            .build()
        leadRepository.findById(lead.id) >> Optional.of(lead)
        leadRepository.save(_ as LeadPacote) >> { LeadPacote l -> l }

        when:
        def resposta = service.atualizarStatus(lead.id, StatusLead.CONTATADO)

        then:
        resposta.status() == 'CONTATADO'
        lead.status == StatusLead.CONTATADO
    }

    private LeadPacoteRequest request() {
        return new LeadPacoteRequest(
            pacoteId, 'Maria', 'maria@exemplo.com', '11999998888',
            'DIGITAL', 'PIX', 2, null)
    }

    private Pacote pacote(boolean ativo) {
        return Pacote.builder()
            .id(pacoteId)
            .nome('Ensaio')
            .ativo(ativo)
            .valorBase(new BigDecimal('500.00'))
            .precoFotoExtra(new BigDecimal('25.00'))
            .build()
    }
}
