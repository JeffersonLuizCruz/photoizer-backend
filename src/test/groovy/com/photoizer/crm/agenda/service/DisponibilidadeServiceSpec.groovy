package com.photoizer.crm.agenda.service

import com.photoizer.crm.agenda.exception.ConflitoDeAgendaException
import com.photoizer.crm.agenda.model.Agendamento
import com.photoizer.crm.agenda.model.StatusAgendamento
import com.photoizer.crm.agenda.repository.AgendamentoRepository
import com.photoizer.crm.shared.port.BloqueioAgendaPort
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.time.LocalDate

import static com.photoizer.crm.agenda.support.AgendaFixtures.agendamento
import static com.photoizer.crm.agenda.support.AgendaFixtures.pacote

class DisponibilidadeServiceSpec extends Specification {

    static final UUID FOTOGRAFO_ID = UUID.randomUUID()
    static final LocalDate DATA = LocalDate.of(2026, 3, 10)
    static final String HORA = "08:00"

    def repository = Mock(AgendamentoRepository)
    def bloqueioAgendaPort = Mock(BloqueioAgendaPort)
    def bloqueios = []

    @Subject
    def service = new DisponibilidadeService(repository, bloqueioAgendaPort)

    def "verificarDisponibilidade ignora ensaio FINALIZADO no mesmo horário"() {
        given: "existe um ensaio finalizado no mesmo horário"
        stubAgendaDoDia(agendamentoNoDia(StatusAgendamento.FINALIZADO))

        when: "a disponibilidade é consultada"
        def resultado = service.verificarDisponibilidade(DATA, HORA, 60, null, false, FOTOGRAFO_ID)

        then: "o horário está disponível"
        resultado.disponivel()
        resultado.conflitos().isEmpty()
    }

    def "verificarDisponibilidade mantém conflito para ensaio CONFIRMADO"() {
        given: "existe um ensaio confirmado no mesmo horário"
        stubAgendaDoDia(agendamentoNoDia(StatusAgendamento.CONFIRMADO))

        when: "a disponibilidade é consultada"
        def resultado = service.verificarDisponibilidade(DATA, HORA, 60, null, false, FOTOGRAFO_ID)

        then: "o horário está indisponível"
        !resultado.disponivel()
        resultado.conflitos().size() == 1
    }

    def "validarConflitoAgenda libera horário quando o ensaio está FINALIZADO"() {
        given: "existe um ensaio finalizado no mesmo horário"
        stubAgendaDoDia(agendamentoNoDia(StatusAgendamento.FINALIZADO))

        when: "o conflito é validado"
        service.validarConflitoAgenda(
            pacote(), DATA.atTime(8, 0), 60, "Estúdio",
            ConflitoAgendaParams.paraCriacao(FOTOGRAFO_ID))

        then: "nenhuma exceção é lançada"
        noExceptionThrown()
    }

    def "validarConflitoAgenda ainda barra ensaio CONFIRMADO no mesmo horário"() {
        given: "existe um ensaio confirmado no mesmo horário"
        stubAgendaDoDia(agendamentoNoDia(StatusAgendamento.CONFIRMADO))

        when: "o conflito é validado"
        service.validarConflitoAgenda(
            pacote(), DATA.atTime(8, 0), 60, "Estúdio",
            ConflitoAgendaParams.paraCriacao(FOTOGRAFO_ID))

        then: "um conflito de agenda é lançado"
        thrown(ConflitoDeAgendaException)
    }

    def "bloqueio global torna o horário indisponível"() {
        given: "existe um bloqueio global no período"
        stubAgendaDoDia()
        bloqueios << bloqueio("Congresso")

        when: "a disponibilidade é consultada"
        def resultado = service.verificarDisponibilidade(DATA, HORA, 60, null, false, FOTOGRAFO_ID)

        then: "o horário está indisponível e o bloqueio é reportado"
        !resultado.disponivel()
        resultado.bloqueios().size() == 1
    }

    def "validarConflitoAgenda barra período bloqueado"() {
        given: "existe um bloqueio global no período"
        stubAgendaDoDia()
        bloqueios << bloqueio("Congresso")

        when: "o conflito é validado"
        service.validarConflitoAgenda(
            pacote(), DATA.atTime(9, 0), 60, "Estúdio",
            ConflitoAgendaParams.paraCriacao(FOTOGRAFO_ID))

        then: "o conflito cita o motivo do bloqueio"
        def exception = thrown(ConflitoDeAgendaException)
        exception.message.contains("Congresso")
    }

    @Unroll
    def "status #status não ocupa a agenda? #ignorado"() {
        given: "existe um ensaio com o status informado"
        stubAgendaDoDia(agendamentoNoDia(StatusAgendamento.valueOf(status)))

        when: "a disponibilidade é consultada"
        def resultado = service.verificarDisponibilidade(DATA, HORA, 60, null, false, FOTOGRAFO_ID)

        then: "o resultado respeita a regra de ocupação"
        resultado.disponivel() == ignorado

        where:
        status                     | ignorado
        "CANCELADO"                | true
        "NO_SHOW"                  | true
        "PRE_RESERVA"              | true
        "AGUARDANDO_APROVACAO"     | true
        "PAGAMENTO_CONFIRMADO"     | true
        "FINALIZADO"               | true
        "CONFIRMADO"               | false
        "REALIZADO"                | false
        "AGUARDANDO_PAGAMENTO_FINAL" | false
        "EM_EDICAO"                | false
    }

    def "pacote que bloqueia o dia inteiro conflita com qualquer ensaio ativo no dia"() {
        given: "existe um ensaio ativo no dia"
        repository.existsByFotografoIdAndDataHoraEnsaioBetweenAndStatusNotIn(_, _, _, _) >> true
        repository.findBloqueiaDiaInteiroByFotografoAndDataBetween(_, _, _, _) >> []
        bloqueioAgendaPort.buscarBloqueios(_, _, _) >> []

        when: "um pacote de dia inteiro é validado"
        service.validarConflitoAgenda(
            pacote(bloqueiaDiaInteiro: true), DATA.atTime(10, 0), 60, "Estúdio",
            ConflitoAgendaParams.paraCriacao(FOTOGRAFO_ID))

        then: "um conflito é lançado"
        thrown(ConflitoDeAgendaException)
    }

    private void stubAgendaDoDia(Agendamento... agendamentos) {
        def existentes = agendamentos.toList()
        repository.findActiveByFotografoAndDataBetween(_, _, _, _) >> { args ->
            filtrarIgnorados(existentes, (List<StatusAgendamento>) args[3])
        }
        repository.findBloqueiaDiaInteiroByFotografoAndDataBetween(_, _, _, _) >> []
        bloqueioAgendaPort.buscarBloqueios(_, _, _) >> { bloqueios }
    }

    private List<Agendamento> filtrarIgnorados(List<Agendamento> existentes,
                                                List<StatusAgendamento> ignorados) {
        existentes.findAll { !ignorados.contains(it.status) }
    }

    private Agendamento agendamentoNoDia(StatusAgendamento status) {
        agendamento(
            status: status,
            dataHoraEnsaio: DATA.atTime(8, 0),
            duracaoMinutos: 60,
            localEnsaio: "Estúdio")
    }

    private BloqueioAgendaPort.BloqueioConflito bloqueio(String titulo) {
        new BloqueioAgendaPort.BloqueioConflito(
            UUID.randomUUID(), titulo, DATA.atTime(8, 0), DATA.atTime(12, 0), null)
    }
}
