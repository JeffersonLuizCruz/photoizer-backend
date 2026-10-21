package com.photoizer.crm.agenda.service

import com.photoizer.crm.agenda.event.AgendamentoCanceladoEvent
import com.photoizer.crm.agenda.event.AgendamentoConfirmadoEvent
import com.photoizer.crm.agenda.event.AgendamentoRealizadoEvent
import com.photoizer.crm.agenda.event.PagamentoFinalRegistradoEvent
import com.photoizer.crm.agenda.exception.StatusAgendamentoInvalidoException
import com.photoizer.crm.agenda.model.StatusAgendamento
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository
import com.photoizer.crm.agenda.repository.AgendamentoRepository
import com.photoizer.crm.shared.model.FormaPagamento
import com.photoizer.crm.shared.storage.FileStorageService
import com.photoizer.crm.shared.storage.FileValidator
import org.springframework.context.ApplicationEventPublisher
import org.springframework.web.multipart.MultipartFile
import spock.lang.Specification
import spock.lang.Subject

import java.time.LocalDate
import java.time.LocalDateTime

import static com.photoizer.crm.agenda.support.AgendaFixtures.agendamento
import static com.photoizer.crm.agenda.support.AgendaFixtures.fotografo
import static com.photoizer.crm.agenda.support.AgendaFixtures.repasse

class AgendamentoStatusLifecycleSpec extends Specification {

    def agendamentoRepository = Mock(AgendamentoRepository)
    def agendamentoFotografoRepository = Mock(AgendamentoFotografoRepository)
    def disponibilidadeService = Mock(DisponibilidadeService)
    def eventPublisher = Mock(ApplicationEventPublisher)
    def fileStorageService = Mock(FileStorageService)
    def fileValidator = Mock(FileValidator)

    @Subject
    def service = new AgendamentoStatusLifecycle(
        agendamentoRepository,
        agendamentoFotografoRepository,
        disponibilidadeService,
        eventPublisher,
        fileStorageService,
        fileValidator)

    def "atualiza status e publica evento de realização"() {
        given: "um agendamento confirmado com fotógrafo vinculado"
        def agendamento = agendamento(
            status: StatusAgendamento.CONFIRMADO, cliente: com.photoizer.crm.agenda.support.AgendaFixtures.cliente())
        def fotografo = fotografo()
        stubPersistencia(agendamento)
        agendamentoFotografoRepository.findByAgendamentoId(agendamento.id) >> [
            repasse(agendamento, fotografo)
        ]

        when: "o status é alterado para REALIZADO"
        def resultado = service.atualizarStatus(agendamento.id, "REALIZADO")

        then: "o evento é publicado após a persistência"
        resultado.status == StatusAgendamento.REALIZADO
        1 * agendamentoRepository.save(agendamento) >> agendamento
        1 * eventPublisher.publishEvent(_ as AgendamentoRealizadoEvent)
    }

    def "rejeita transição de status inválida"() {
        given: "um agendamento finalizado"
        def agendamento = agendamento(status: StatusAgendamento.FINALIZADO)
        agendamentoRepository.findByIdWithLock(agendamento.id) >> Optional.of(agendamento)

        when: "tenta voltar para CONFIRMADO"
        service.atualizarStatus(agendamento.id, "CONFIRMADO")

        then: "a transição é bloqueada e nada é salvo"
        thrown(StatusAgendamentoInvalidoException)
        0 * agendamentoRepository.save(_)
        0 * eventPublisher.publishEvent(_)
    }

    def "publica evento de cancelamento"() {
        given: "um agendamento confirmado"
        def agendamento = agendamento(status: StatusAgendamento.CONFIRMADO)
        stubPersistencia(agendamento)

        when: "o agendamento é cancelado"
        service.atualizarStatus(agendamento.id, "CANCELADO")

        then: "o evento de cancelamento é publicado"
        agendamento.status == StatusAgendamento.CANCELADO
        1 * eventPublisher.publishEvent(_ as AgendamentoCanceladoEvent)
    }

    def "reagenda validando conflito e publica confirmação"() {
        given: "um agendamento confirmado"
        def agendamento = agendamento(
            status: StatusAgendamento.CONFIRMADO, cliente: com.photoizer.crm.agenda.support.AgendaFixtures.cliente())
        agendamento.fotografo = fotografo()
        stubPersistencia(agendamento)
        def novaData = LocalDate.now().plusDays(30)

        when: "o ensaio é reagendado"
        def resultado = service.reagendar(agendamento.id, novaData, "14:00", 90)

        then: "o conflito é validado, a data é atualizada e o evento é publicado"
        resultado.dataHoraEnsaio == LocalDateTime.of(novaData, java.time.LocalTime.of(14, 0))
        resultado.duracaoMinutos == 90
        1 * disponibilidadeService.validarConflitoAgenda(*_)
        1 * eventPublisher.publishEvent(_ as AgendamentoConfirmadoEvent)
    }

    def "alterna destaque do ensaio"() {
        given: "um agendamento sem destaque"
        def agendamento = agendamento(ensaioDestaque: false)
        stubPersistencia(agendamento)

        when: "o destaque é alternado"
        def resultado = service.toggleDestaque(agendamento.id)

        then: "o estado é persistido"
        resultado.ensaioDestaque
        1 * agendamentoRepository.save(agendamento) >> agendamento
    }

    def "registra pagamento final sem comprovante e transita para edição"() {
        given: "um agendamento aguardando pagamento final"
        def agendamento = agendamento(
            status: StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL,
            valorTotalFinal: new BigDecimal("1000.00"),
            valorRestante: new BigDecimal("700.00"))
        stubPersistencia(agendamento)

        when: "o pagamento final é registrado sem anexo"
        def resultado = service.registrarPagamentoFinal(agendamento.id, null, FormaPagamento.PIX)

        then: "o ensaio entra em edição sem comprovante"
        resultado.status == StatusAgendamento.EM_EDICAO
        resultado.urlComprovanteFinal == null
        resultado.valorRestante == BigDecimal.ZERO
        0 * fileStorageService.salvar(_)
        1 * eventPublisher.publishEvent(_ as PagamentoFinalRegistradoEvent)
    }

    def "registra pagamento final com comprovante e mantém a URL"() {
        given: "um agendamento aguardando pagamento final"
        def agendamento = agendamento(
            status: StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL,
            valorTotalFinal: new BigDecimal("1000.00"),
            valorRestante: new BigDecimal("700.00"))
        def comprovante = Mock(MultipartFile)
        comprovante.isEmpty() >> false
        stubPersistencia(agendamento)
        fileStorageService.salvar(comprovante) >> "uploads/comprovante.pdf"

        when: "o pagamento final é registrado com anexo"
        def resultado = service.registrarPagamentoFinal(agendamento.id, comprovante, FormaPagamento.PIX)

        then: "o arquivo é validado, salvo e a URL é persistida"
        1 * fileValidator.validate(comprovante, "any")
        1 * fileStorageService.salvar(comprovante) >> "uploads/comprovante.pdf"
        resultado.urlComprovanteFinal == "uploads/comprovante.pdf"
    }

    private void stubPersistencia(def agendamento) {
        agendamentoRepository.findByIdWithLock(agendamento.id) >> Optional.of(agendamento)
        agendamentoRepository.save(agendamento) >> agendamento
        agendamentoFotografoRepository.findByAgendamentoId(agendamento.id) >> []
    }
}
