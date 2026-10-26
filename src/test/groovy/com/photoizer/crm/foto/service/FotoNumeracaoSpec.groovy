package com.photoizer.crm.foto.service

import com.photoizer.crm.foto.acl.AgendamentoReadService
import com.photoizer.crm.foto.exception.FotoNaoEditavelException
import com.photoizer.crm.foto.exception.RenumeracaoNaoPermitidaException
import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.model.StatusFoto
import com.photoizer.crm.foto.repository.FotoEnsaioRepository
import com.photoizer.crm.shared.storage.FileStorageService
import com.photoizer.crm.shared.storage.FileValidator
import com.photoizer.crm.shared.storage.StoragePathResolver
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.Subject

class FotoNumeracaoSpec extends Specification {

    def agendamentoId = UUID.randomUUID()
    def fotoRepository = Mock(FotoEnsaioRepository)
    def agendamentoReadService = Mock(AgendamentoReadService)

    @Subject
    def service = new FotoService(
        fotoRepository,
        Mock(FileStorageService),
        Mock(FotoProcessingHelper),
        Mock(FotoPreviewService),
        agendamentoReadService,
        Mock(FileValidator),
        Mock(StoragePathResolver),
        Mock(ApplicationEventPublisher)
    )

    def "publica e numera as fotos sequencialmente na ordem de exibicao"() {
        given:
        def primeira = foto(ordem: 0, status: StatusFoto.INEDITA)
        def segunda = foto(ordem: 1, status: StatusFoto.INEDITA)
        def terceira = foto(ordem: 2, status: StatusFoto.INEDITA)
        agendamentoReadService.isStatusPermitidoParaPublicacao(agendamentoId) >> true
        fotoRepository.findAllByAgendamentoIdForUpdate(agendamentoId) >> [primeira, segunda, terceira]
        fotoRepository.saveAll(_ as Iterable) >> { args -> args[0] as List }

        when:
        def publicadas = service.publicar(agendamentoId)

        then:
        publicadas*.numeroGaleria == [1, 2, 3]
        publicadas*.status.every { it == StatusFoto.PUBLICADA }
    }

    def "publicacao posterior continua a numeracao sem renumerar as existentes"() {
        given:
        def existente1 = foto(ordem: 0, status: StatusFoto.PUBLICADA, numero: 1)
        def existente2 = foto(ordem: 1, status: StatusFoto.PUBLICADA, numero: 2)
        def nova = foto(ordem: 2, status: StatusFoto.INEDITA)
        agendamentoReadService.isStatusPermitidoParaPublicacao(agendamentoId) >> true
        fotoRepository.findAllByAgendamentoIdForUpdate(agendamentoId) >> [existente1, existente2, nova]
        fotoRepository.saveAll(_ as Iterable) >> { args -> args[0] as List }

        when:
        def publicadas = service.publicar(agendamentoId)

        then:
        existente1.numeroGaleria == 1
        existente2.numeroGaleria == 2
        nova.numeroGaleria == 3
        publicadas == [nova]
    }

    def "foto oculta nao recebe numero na publicacao"() {
        given:
        def visivel = foto(ordem: 0, status: StatusFoto.INEDITA)
        def oculta = foto(ordem: 1, status: StatusFoto.INEDITA, visivel: false)
        agendamentoReadService.isStatusPermitidoParaPublicacao(agendamentoId) >> true
        fotoRepository.findAllByAgendamentoIdForUpdate(agendamentoId) >> [visivel, oculta]
        fotoRepository.saveAll(_ as Iterable) >> { args -> args[0] as List }

        when:
        service.publicar(agendamentoId)

        then:
        visivel.numeroGaleria == 1
        oculta.numeroGaleria == null
        oculta.status == StatusFoto.PUBLICADA
    }

    def "ao tornar visivel uma foto publicada sem numero, atribui o proximo"() {
        given:
        def primeira = foto(ordem: 0, status: StatusFoto.PUBLICADA, numero: 1)
        def oculta = foto(ordem: 1, status: StatusFoto.PUBLICADA, visivel: false)
        fotoRepository.findAllByAgendamentoIdForUpdate(agendamentoId) >> [primeira, oculta]
        fotoRepository.save(_ as FotoEnsaio) >> { args -> args[0] }

        when:
        def atualizada = service.alterarVisibilidade(agendamentoId, oculta.id, true)

        then:
        atualizada.numeroGaleria == 2
        atualizada.visivel
    }

    def "renumerar reatribui 1..N apenas para fotos publicadas e visiveis"() {
        given:
        def primeira = foto(ordem: 0, status: StatusFoto.PUBLICADA, numero: 10)
        def segunda = foto(ordem: 1, status: StatusFoto.PUBLICADA, numero: 20)
        def oculta = foto(ordem: 2, status: StatusFoto.PUBLICADA, numero: 30, visivel: false)
        fotoRepository.findAllByAgendamentoIdForUpdate(agendamentoId) >> [primeira, segunda, oculta]
        fotoRepository.saveAll(_ as Iterable) >> { args -> args[0] as List }

        when:
        service.renumerar(agendamentoId)

        then:
        primeira.numeroGaleria == 1
        segunda.numeroGaleria == 2
        oculta.numeroGaleria == null
    }

    def "renumerar e bloqueado quando alguma foto ja foi selecionada ou baixada"() {
        given:
        def selecionada = foto(ordem: 0, status: StatusFoto.PUBLICADA, numero: 1, selecionada: true)
        fotoRepository.findAllByAgendamentoIdForUpdate(agendamentoId) >> [selecionada]

        when:
        service.renumerar(agendamentoId)

        then:
        def erro = thrown(RenumeracaoNaoPermitidaException)
        erro.message.contains("1 foto")
        0 * fotoRepository.saveAll(_ as Iterable)
    }

    def "nao oculta foto ja selecionada no pacote"() {
        given:
        def selecionada = foto(status: StatusFoto.PUBLICADA, selecionada: true)
        fotoRepository.findAllByAgendamentoIdForUpdate(agendamentoId) >> [selecionada]

        when:
        service.alterarVisibilidade(agendamentoId, selecionada.id, false)

        then:
        def erro = thrown(FotoNaoEditavelException)
        erro.message.contains("não pode ser ocultada")
        0 * fotoRepository.save(_ as FotoEnsaio)
    }

    def "nao altera status de foto baixada"() {
        given:
        def baixada = foto(status: StatusFoto.PUBLICADA, download: java.time.LocalDateTime.now())
        fotoRepository.findAllByAgendamentoIdForUpdate(agendamentoId) >> [baixada]

        when:
        service.alterarStatus(agendamentoId, baixada.id, StatusFoto.INEDITA)

        then:
        def erro = thrown(FotoNaoEditavelException)
        erro.message.contains("não pode ter o status alterado")
        0 * fotoRepository.save(_ as FotoEnsaio)
    }

    def "nao substitui imagem de foto ja selecionada no pacote"() {
        given:
        def selecionada = foto(status: StatusFoto.PUBLICADA, selecionada: true)
        fotoRepository.findById(selecionada.id) >> Optional.of(selecionada)

        when:
        service.substituirImagem(agendamentoId, selecionada.id, null)

        then:
        def erro = thrown(FotoNaoEditavelException)
        erro.message.contains("vinculada")
        0 * fotoRepository.save(_ as FotoEnsaio)
    }

    private FotoEnsaio foto(Map overrides = [:]) {
        return FotoEnsaio.builder()
            .id(overrides.id ?: UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .fileName(overrides.fileName ?: "IMG_${UUID.randomUUID()}.jpg")
            .originalPath("orig/foto.jpg")
            .watermarkedPath("wm/foto.jpg")
            .thumbPath("thumb/foto.jpg")
            .ordem(overrides.ordem ?: 0)
            .numeroGaleria(overrides.numero)
            .status(overrides.status ?: StatusFoto.PUBLICADA)
            .selecionadaPacote(overrides.selecionada ?: false)
            .dataDownload(overrides.download)
            .visivel(overrides.visivel != null ? overrides.visivel : true)
            .build()
    }
}
