package com.photoizer.crm.ecommerce.service

import com.photoizer.crm.agenda.model.Agendamento
import com.photoizer.crm.agenda.model.StatusAgendamento
import com.photoizer.crm.agenda.repository.AgendamentoRepository
import com.photoizer.crm.ecommerce.model.EtapaGaleria
import com.photoizer.crm.ecommerce.model.FaseGaleria
import com.photoizer.crm.ecommerce.model.GaleriaEnsaio
import com.photoizer.crm.ecommerce.model.ModoGaleria
import com.photoizer.crm.ecommerce.model.SelecaoEnsaio
import com.photoizer.crm.ecommerce.model.StatusSelecao
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository
import com.photoizer.crm.ecommerce.repository.SelecaoEnsaioRepository
import com.photoizer.crm.foto.repository.FotoEnsaioRepository
import spock.lang.Specification
import spock.lang.Subject

import java.time.LocalDateTime

class GaleriaPipelineQueryServiceSpec extends Specification {

    def agendamentoId = UUID.randomUUID()
    def token = UUID.randomUUID()

    def agendamentoRepository = Mock(AgendamentoRepository)
    def fotoRepository = Mock(FotoEnsaioRepository)
    def selecaoRepository = Mock(SelecaoEnsaioRepository)
    def compraRepository = Mock(CompraExtraRepository)
    def galeriaEnsaioService = Mock(GaleriaEnsaioService)
    def galeriaQueryService = Mock(GaleriaQueryService)

    @Subject
    def service = new GaleriaPipelineQueryService(
        agendamentoRepository,
        fotoRepository,
        selecaoRepository,
        compraRepository,
        galeriaEnsaioService,
        galeriaQueryService)

    def "status publico continua respondendo para galeria sem fotos"() {
        given: "uma galeria finalizada que ainda nao recebeu fotos no CRM"
        def agendamento = agendamento(StatusAgendamento.FINALIZADO)
        prepararContexto(agendamento, null, null)
        galeriaQueryService.buscarAgendamentoPorToken(token) >> agendamento
        galeriaEnsaioService.obter(agendamentoId) >> galeria(FaseGaleria.SELECAO)

        when: "o cliente consulta o status pelo token"
        def status = service.statusPublico(token)

        then: "a etapa continua sendo aguardando upload, sem erro de lista vazia"
        status.etapa() == EtapaGaleria.AGUARDANDO_UPLOAD
        status.totalFotos() == 0
        !status.clienteBaixou()
        status.clienteBaixouEm() == null
    }

    def "galerias sem fotos ficam fora da fila por padrao e aparecem com semFotos"() {
        given:
        def agendamento = agendamento(StatusAgendamento.FINALIZADO)
        prepararContexto(agendamento, null, null)

        when:
        def filaPadrao = service.listar(null, null, false, 1, 20, true, false)
        def filaComSemFotos = service.listar(null, null, false, 1, 20, true, true)

        then:
        filaPadrao.data.isEmpty()
        filaPadrao.total == 0
        filaComSemFotos.data.size() == 1
        filaComSemFotos.data[0].etapa() == EtapaGaleria.AGUARDANDO_UPLOAD
    }

    def "filtro explicito por aguardando upload inclui galerias sem fotos"() {
        given:
        def agendamento = agendamento(StatusAgendamento.FINALIZADO)
        prepararContexto(agendamento, null, null)

        when:
        def fila = service.listar(EtapaGaleria.AGUARDANDO_UPLOAD, null, false, 1, 20, true, false)

        then:
        fila.data.size() == 1
    }

    def "resumo mantem aguardandoUpload zerado e expoe semFotosNoCrm"() {
        given:
        def agendamento = agendamento(StatusAgendamento.FINALIZADO)
        prepararContexto(agendamento, null, null)

        when:
        def resumo = service.resumo(true)

        then:
        resumo.total() == 0
        resumo.aguardandoUpload() == 0
        resumo.semFotosNoCrm() == 1
    }

    def "compra pendente vira etapa mesmo sem permissao financeira"() {
        given: "galeria comercializada com uma compra aguardando confirmacao"
        def agendamento = agendamento(StatusAgendamento.EM_EDICAO)
        def fotos = Stub(FotoEnsaioRepository.GaleriaPipelineFotos) {
            getAgendamentoId() >> agendamentoId
            getTotal() >> 10
            getPublicadas() >> 10
            getArquivadas() >> 0
            getSelecionadas() >> 10
            getSelecionadasBaixadas() >> 0
            getDownloadPacoteEm() >> null
            getBaixadas() >> 0
            getExtrasPendentesEdicao() >> 0
        }
        def compras = Stub(CompraExtraRepository.CompraPipelineAgregado) {
            getAgendamentoId() >> agendamentoId
            getPendentes() >> 1
            getAguardandoComprovante() >> 0
            getAguardandoConfirmacao() >> 1
            getPrimeiraPendenteEm() >> LocalDateTime.now().minusDays(2)
            getValorPago() >> BigDecimal.ZERO
        }
        def selecao = Stub(SelecaoEnsaio) {
            getAgendamentoId() >> agendamentoId
            getStatus() >> StatusSelecao.PUBLICADA
            getNumero() >> 1
            getEnviadaEm() >> LocalDateTime.now().minusDays(5)
            getPublicadaEm() >> LocalDateTime.now().minusDays(3)
        }
        agendamentoRepository.findParaPipeline(_, _) >> [agendamento]
        fotoRepository.agregarPipeline(_) >> [fotos]
        compraRepository.agregarPipeline(_, _) >> [compras]
        selecaoRepository.findByAgendamentoIdInAndStatusInOrderByNumeroDesc(_, _) >> [selecao]
        galeriaEnsaioService.obterEmLote(_) >> [
            (agendamentoId): galeria(FaseGaleria.COMERCIALIZACAO)
        ]

        when: "um usuario sem permissao financeira consulta a fila"
        def fila = service.listar(null, null, false, 1, 20, false, false)

        then: "a etapa nao depende da permissao, mas o valor financeiro fica oculto"
        fila.data.size() == 1
        fila.data[0].etapa() == EtapaGaleria.COMPRA_EXTRA_PENDENTE
        fila.data[0].comprasPendentes() == 0
        fila.data[0].comprasAguardandoConfirmacao() == 1
    }

    private void prepararContexto(Agendamento agendamento, fotos, compras) {
        agendamentoRepository.findParaPipeline(_, _) >> [agendamento]
        fotoRepository.agregarPipeline(_) >> (fotos == null ? [] : [fotos])
        compraRepository.agregarPipeline(_, _) >> (compras == null ? [] : [compras])
        selecaoRepository.findByAgendamentoIdInAndStatusInOrderByNumeroDesc(_, _) >> []
        galeriaEnsaioService.obterEmLote(_) >> [
            (agendamentoId): galeria(FaseGaleria.SELECAO)
        ]
    }

    private Agendamento agendamento(StatusAgendamento status) {
        Agendamento.builder()
            .id(agendamentoId)
            .status(status)
            .dataHoraEnsaio(LocalDateTime.now().minusDays(10))
            .build()
    }

    private GaleriaEnsaio galeria(FaseGaleria fase) {
        GaleriaEnsaio.builder()
            .agendamentoId(agendamentoId)
            .modo(ModoGaleria.COM_EDICAO)
            .fase(fase)
            .build()
    }
}
