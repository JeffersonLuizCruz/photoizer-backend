package com.photoizer.crm.agenda.service

import com.photoizer.crm.agenda.api.AgendamentoMapper
import com.photoizer.crm.agenda.model.StatusAgendamento
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository
import com.photoizer.crm.agenda.repository.AgendamentoRepository
import com.photoizer.crm.agenda.repository.ReatribuicaoAgendamentoRepository
import com.photoizer.crm.agenda.support.AgendaFixtures
import com.photoizer.crm.auth.repository.UserRepository
import com.photoizer.crm.cliente.repository.ClienteRepository
import com.photoizer.crm.config.service.ConfiguracaoService
import com.photoizer.crm.foto.repository.FotoEnsaioRepository
import com.photoizer.crm.pacote.service.PacoteQueryService
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

import java.time.LocalDateTime
import java.util.UUID

class AgendamentoClienteServiceSpec extends Specification {

    def clienteId = UUID.randomUUID()
    def agendamentoRepository = Mock(AgendamentoRepository)
    def fotoEnsaioRepository = Mock(FotoEnsaioRepository)
    def service = new AgendamentoService(
        Mock(ClienteRepository),
        Mock(PacoteQueryService),
        Mock(UserRepository),
        agendamentoRepository,
        Mock(ApplicationEventPublisher),
        fotoEnsaioRepository,
        Mock(ConfiguracaoService),
        Mock(AgendamentoFotografoRepository),
        Mock(ReatribuicaoAgendamentoRepository),
        Mock(DisponibilidadeService),
        Mock(PartilhaService),
        Mock(AgendamentoValoresCalculator),
        Mock(AgendamentoMapper))

    def "lista apenas ensaios confirmados em diante e usa os contadores agregados"() {
        given: "um ensaio confirmado com fotos publicadas, pagas e selecionadas"
        def agendamento = AgendaFixtures.agendamento(
            status: StatusAgendamento.CONFIRMADO,
            pacote: AgendaFixtures.pacote(nome: "Essencial"),
            dataHoraEnsaio: LocalDateTime.now().plusDays(3))
        def agregado = Stub(FotoEnsaioRepository.GaleriaPipelineFotos) {
            getAgendamentoId() >> agendamento.id
            getPublicadas() >> 7L
            getSelecionadas() >> 3L
            getPagas() >> 2L
        }

        when: "a área do cliente consulta os ensaios"
        def resultado = service.listarAgendamentosCliente(clienteId)

        then: "a query exclui pré-reserva, cancelado e no-show"
        1 * agendamentoRepository.findByClienteIdAndStatusInOrderByDataHoraEnsaioDesc(
            clienteId,
            { statuses ->
                statuses.containsAll([
                    StatusAgendamento.CONFIRMADO,
                    StatusAgendamento.REALIZADO,
                    StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL,
                    StatusAgendamento.EM_EDICAO,
                    StatusAgendamento.FINALIZADO])
                && !statuses.contains(StatusAgendamento.PRE_RESERVA)
                && !statuses.contains(StatusAgendamento.AGUARDANDO_APROVACAO)
                && !statuses.contains(StatusAgendamento.PAGAMENTO_CONFIRMADO)
                && !statuses.contains(StatusAgendamento.CANCELADO)
                && !statuses.contains(StatusAgendamento.NO_SHOW)
            }) >> [agendamento]

        and: "as fotos são agregadas uma única vez"
        1 * fotoEnsaioRepository.agregarPipeline([agendamento.id]) >> [agregado]

        and: "o DTO reflete os contadores e a descrição do status"
        resultado.size() == 1
        resultado[0].pacoteNome == "Essencial"
        resultado[0].status == "CONFIRMADO"
        resultado[0].statusDescricao == "Confirmado"
        resultado[0].totalFotosPublicadas == 7
        resultado[0].fotosSelecionadasPacote == 3
        resultado[0].fotosPagas == 2
    }

    def "não consulta fotos quando o cliente não tem ensaios visíveis"() {
        given:
        agendamentoRepository.findByClienteIdAndStatusInOrderByDataHoraEnsaioDesc(clienteId, _) >> []

        when:
        def resultado = service.listarAgendamentosCliente(clienteId)

        then:
        resultado.isEmpty()
        0 * fotoEnsaioRepository.agregarPipeline(_)
    }
}
