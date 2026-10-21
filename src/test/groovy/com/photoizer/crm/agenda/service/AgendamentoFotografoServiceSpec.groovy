package com.photoizer.crm.agenda.service

import com.photoizer.crm.agenda.exception.EnsaioNaoFinalizadoException
import com.photoizer.crm.agenda.model.RepasseStatus
import com.photoizer.crm.agenda.model.StatusAgendamento
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository
import com.photoizer.crm.agenda.repository.AgendamentoRepository
import com.photoizer.crm.auth.repository.UserRepository
import com.photoizer.crm.shared.exception.BadRequestException
import com.photoizer.crm.shared.exception.ConflictException
import com.photoizer.crm.shared.model.TipoRepasse
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.photoizer.crm.agenda.support.AgendaFixtures.agendamento
import static com.photoizer.crm.agenda.support.AgendaFixtures.fotografo
import static com.photoizer.crm.agenda.support.AgendaFixtures.repasse

class AgendamentoFotografoServiceSpec extends Specification {

    def agendamentoFotografoRepository = Mock(AgendamentoFotografoRepository)
    def agendamentoRepository = Mock(AgendamentoRepository)
    def userRepository = Mock(UserRepository)
    def partilhaService = Mock(PartilhaService)

    @Subject
    def service = new AgendamentoFotografoService(
        agendamentoFotografoRepository,
        agendamentoRepository,
        userRepository,
        partilhaService,
        new AgendamentoValoresCalculator())

    def "listarPendentes ignora repasses de agendamento cancelado"() {
        given: "existem repasses pendentes de ensaios ativo e cancelado"
        def linkAtivo = repasse(agendamento(status: StatusAgendamento.FINALIZADO))
        def linkCancelado = repasse(agendamento(status: StatusAgendamento.CANCELADO))
        agendamentoFotografoRepository.findByStatusWithAgendamento(RepasseStatus.PENDENTE) >>
            [linkAtivo, linkCancelado]

        when: "os repasses pendentes são listados"
        def resultado = service.listarPendentes()

        then: "somente o repasse do ensaio ativo é retornado"
        resultado == [linkAtivo]
    }

    def "listarPendentesPorFotografo ignora repasses de agendamento cancelado"() {
        given: "existem repasses pendentes de ensaios ativo e cancelado"
        def fotografoId = UUID.randomUUID()
        def linkAtivo = repasse(agendamento(status: StatusAgendamento.REALIZADO))
        def linkCancelado = repasse(agendamento(status: StatusAgendamento.CANCELADO))
        agendamentoFotografoRepository.findByFotografoIdAndStatusWithAgendamento(
            fotografoId, RepasseStatus.PENDENTE) >> [linkAtivo, linkCancelado]

        when: "os repasses pendentes do fotógrafo são listados"
        def resultado = service.listarPendentesPorFotografo(fotografoId)

        then: "somente o repasse do ensaio ativo é retornado"
        resultado == [linkAtivo]
    }

    def "paga repasse de ensaio finalizado"() {
        given: "um repasse pendente de ensaio finalizado"
        def agendamento = agendamento(status: StatusAgendamento.FINALIZADO)
        def fotografo = fotografo()
        def link = repasse(agendamento, fotografo)
        agendamentoFotografoRepository.findByAgendamentoId(agendamento.id) >> [link]
        agendamentoFotografoRepository.save(link) >> link

        when: "o repasse é pago"
        def resultado = service.pagarRepasse(agendamento.id, fotografo.id)

        then: "o status e a data de pagamento são registrados"
        resultado.status == RepasseStatus.PAGO
        resultado.dataPagamento != null
    }

    @Unroll
    def "não paga repasse com status #status"() {
        given: "um repasse no status informado"
        def agendamento = agendamento(status: StatusAgendamento.FINALIZADO)
        def fotografo = fotografo()
        def link = repasse(agendamento, fotografo, RepasseStatus.valueOf(status))
        agendamentoFotografoRepository.findByAgendamentoId(agendamento.id) >> [link]

        when: "o repasse é pago"
        service.pagarRepasse(agendamento.id, fotografo.id)

        then: "o pagamento é bloqueado"
        thrown(ConflictException)

        where:
        status << ["PAGO", "CANCELADO"]
    }

    def "não paga repasse antes da finalização do ensaio"() {
        given: "um repasse pendente de ensaio confirmado"
        def agendamento = agendamento(status: StatusAgendamento.CONFIRMADO)
        def fotografo = fotografo()
        def link = repasse(agendamento, fotografo)
        agendamentoFotografoRepository.findByAgendamentoId(agendamento.id) >> [link]

        when: "o repasse é pago"
        service.pagarRepasse(agendamento.id, fotografo.id)

        then: "o ensaio precisa estar finalizado"
        thrown(EnsaioNaoFinalizadoException)
    }

    def "não cancela repasse já pago"() {
        given: "um repasse pago"
        def agendamento = agendamento(status: StatusAgendamento.FINALIZADO)
        def fotografo = fotografo()
        def link = repasse(agendamento, fotografo, RepasseStatus.PAGO)
        agendamentoFotografoRepository.findByAgendamentoId(agendamento.id) >> [link]

        when: "o repasse é cancelado"
        service.cancelarRepasse(agendamento.id, fotografo.id)

        then: "o cancelamento é bloqueado"
        thrown(ConflictException)
    }

    def "adiciona fotógrafo e recalcula a partilha"() {
        given: "um agendamento sem o fotógrafo vinculado"
        def agendamento = agendamento()
        def fotografo = fotografo()
        agendamentoRepository.findById(agendamento.id) >> Optional.of(agendamento)
        userRepository.findById(fotografo.id) >> Optional.of(fotografo)
        agendamentoFotografoRepository.findByAgendamentoId(agendamento.id) >> []
        agendamentoFotografoRepository.save(_) >> { args -> args[0] }

        when: "o fotógrafo é adicionado com repasse fixo"
        def link = service.adicionarFotografo(
            agendamento.id, fotografo.id, new BigDecimal("250.00"), TipoRepasse.FIXO, null)

        then: "o vínculo é criado e a partilha é validada"
        link.valorRepassar == new BigDecimal("250.00")
        link.status == RepasseStatus.PENDENTE
        1 * partilhaService.validarPartilha(agendamento.id)
        1 * partilhaService.calcularPartilhaFotografo(agendamento)
    }

    def "não adiciona fotógrafo já vinculado"() {
        given: "um agendamento com o fotógrafo já vinculado"
        def agendamento = agendamento()
        def fotografo = fotografo()
        agendamentoRepository.findById(agendamento.id) >> Optional.of(agendamento)
        userRepository.findById(fotografo.id) >> Optional.of(fotografo)
        agendamentoFotografoRepository.findByAgendamentoId(agendamento.id) >> [
            repasse(agendamento, fotografo)
        ]

        when: "o fotógrafo é adicionado novamente"
        service.adicionarFotografo(agendamento.id, fotografo.id, new BigDecimal("100.00"), TipoRepasse.FIXO, null)

        then: "um conflito é reportado"
        thrown(ConflictException)
    }

    def "rejeita percentual fora da faixa permitida"() {
        given: "um agendamento e um fotógrafo válidos"
        def agendamento = agendamento()
        def fotografo = fotografo()
        agendamentoRepository.findById(agendamento.id) >> Optional.of(agendamento)
        userRepository.findById(fotografo.id) >> Optional.of(fotografo)
        agendamentoFotografoRepository.findByAgendamentoId(agendamento.id) >> []

        when: "um repasse percentual de 101% é solicitado"
        service.adicionarFotografo(
            agendamento.id, fotografo.id, null, TipoRepasse.PERCENTUAL, new BigDecimal("101.00"))

        then: "a validação bloqueia a operação"
        thrown(BadRequestException)
    }
}
