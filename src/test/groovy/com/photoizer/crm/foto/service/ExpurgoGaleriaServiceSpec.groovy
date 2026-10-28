package com.photoizer.crm.foto.service

import com.photoizer.crm.foto.acl.AgendamentoArmazenamentoReadService
import com.photoizer.crm.foto.event.GaleriaArquivadaEvent
import com.photoizer.crm.foto.exception.ExpurgoNaoPermitidoException
import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.model.StatusFoto
import com.photoizer.crm.foto.repository.FotoEnsaioRepository
import com.photoizer.crm.shared.storage.StoragePathResolver
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Files
import java.nio.file.Path

class ExpurgoGaleriaServiceSpec extends Specification {

    def uploadDir = Files.createTempDirectory("expurgo-spec")
    def fotoRepository = Mock(FotoEnsaioRepository)
    def agendamentoReadService = Mock(AgendamentoArmazenamentoReadService)
    def pathResolver = Mock(StoragePathResolver)
    def armazenamentoService = Mock(ArmazenamentoService)
    def eventPublisher = Mock(ApplicationEventPublisher)

    @Subject
    def service = new ExpurgoGaleriaService(
        fotoRepository, agendamentoReadService, pathResolver, armazenamentoService, eventPublisher)

    def agendamentoId = UUID.randomUUID()

    def setup() {
        pathResolver.getUploadDir() >> uploadDir
        pathResolver.resolver(_) >> { String relativo ->
            relativo == null ? null : uploadDir.resolve(relativo)
        }
    }

    def cleanup() {
        apagar(uploadDir)
    }

    def "apaga os arquivos das fotos e preserva comprovantes e contratos"() {
        given:
        def foto = criarFoto()
        def comprovante = "${agendamentoId}/comprovante_entrada_1.pdf"
        def proposta = "propostas/${agendamentoId}.pdf"
        escrever(comprovante, 20)
        escrever(proposta, 15)
        agendamentoReadService.isFinalizado(agendamentoId) >> true
        fotoRepository.findAllByAgendamentoIdForUpdate(agendamentoId) >> [foto]
        fotoRepository.saveAll(_) >> { args -> args[0] }

        when:
        def resultado = service.expurgar(agendamentoId)

        then:
        resultado.fotos() == 1
        resultado.bytesLiberados() == bytesDaFoto()
        foto.arquivosRemovidos
        foto.arquivosRemovidosEm != null
        foto.arquivosRemovidosPor == 'SYSTEM'
        foto.bytesLiberados == bytesDaFoto()
        !Files.exists(uploadDir.resolve(foto.originalPath))
        !Files.exists(uploadDir.resolve(foto.editadaPath))
        !Files.exists(uploadDir.resolve("${agendamentoId}/preview_v3_${foto.id}_ORIGINAL.jpg"))
        !Files.exists(uploadDir.resolve("${agendamentoId}/preview_v5_${foto.id}_ORIGINAL.jpg"))
        !Files.exists(uploadDir.resolve("${agendamentoId}/preview_v6_protegida_${foto.id}_ORIGINAL.jpg"))
        !Files.exists(uploadDir.resolve("${agendamentoId}/preview_v6_equilibrada_${foto.id}_ORIGINAL.jpg"))
        !Files.exists(uploadDir.resolve("${agendamentoId}/thumb_protegido_${foto.id}_ORIGINAL.jpg"))
        !Files.exists(uploadDir.resolve("${agendamentoId}/thumb_protegido_v2_${foto.id}_ORIGINAL.jpg"))
        !Files.exists(uploadDir.resolve("${agendamentoId}/orig/hero_${foto.id}_ORIGINAL.jpg"))
        Files.exists(uploadDir.resolve(comprovante))
        Files.exists(uploadDir.resolve(proposta))
        1 * eventPublisher.publishEvent({ evento ->
            evento instanceof GaleriaArquivadaEvent
                && evento.agendamentoId() == agendamentoId
                && evento.fotoIds() == [foto.id]
        })
    }

    def "rejeita expurgo de ensaio não finalizado"() {
        given:
        agendamentoReadService.isFinalizado(agendamentoId) >> false

        when:
        service.expurgar(agendamentoId)

        then:
        def erro = thrown(ExpurgoNaoPermitidoException)
        erro.errorCode == com.photoizer.crm.shared.exception.ErrorCode.ENSAIO_NAO_FINALIZADO
        0 * fotoRepository.findAllByAgendamentoIdForUpdate(_)
    }

    def "rejeita galeria com seleção ativa ou compra pendente"() {
        given:
        def foto = criarFoto(status: StatusFoto.AGUARDANDO_CONFIRMACAO)
        agendamentoReadService.isFinalizado(agendamentoId) >> true
        fotoRepository.findAllByAgendamentoIdForUpdate(agendamentoId) >> [foto]

        when:
        service.expurgar(agendamentoId)

        then:
        thrown(ExpurgoNaoPermitidoException)
        !foto.arquivosRemovidos
        Files.exists(uploadDir.resolve(foto.originalPath))
    }

    def "rejeita galeria que já foi arquivada"() {
        given:
        def foto = criarFoto()
        foto.setArquivosRemovidos(true)
        agendamentoReadService.isFinalizado(agendamentoId) >> true
        fotoRepository.findAllByAgendamentoIdForUpdate(agendamentoId) >> [foto]

        when:
        service.expurgar(agendamentoId)

        then:
        def erro = thrown(ExpurgoNaoPermitidoException)
        erro.message.contains("já foi arquivada")
    }

    def "retoma o expurgo de fotos que ainda não foram arquivadas"() {
        given:
        def arquivada = criarFoto()
        arquivada.setArquivosRemovidos(true)
        def pendente = criarFoto()
        agendamentoReadService.isFinalizado(agendamentoId) >> true
        fotoRepository.findAllByAgendamentoIdForUpdate(agendamentoId) >> [arquivada, pendente]
        fotoRepository.saveAll(_) >> { args -> args[0] }

        when:
        def resultado = service.expurgar(agendamentoId)

        then:
        resultado.fotos() == 1
        pendente.arquivosRemovidos
        Files.exists(uploadDir.resolve(arquivada.originalPath))
    }

    def "arquivo já ausente não impede o restante do expurgo"() {
        given:
        def foto = criarFoto()
        Files.delete(uploadDir.resolve(foto.originalPath))
        agendamentoReadService.isFinalizado(agendamentoId) >> true
        fotoRepository.findAllByAgendamentoIdForUpdate(agendamentoId) >> [foto]
        fotoRepository.saveAll(_) >> { args -> args[0] }

        when:
        def resultado = service.expurgar(agendamentoId)

        then:
        resultado.fotos() == 1
        resultado.bytesLiberados() == bytesDaFoto() - 10
        foto.arquivosRemovidos
        !Files.exists(uploadDir.resolve(foto.watermarkedPath))
    }

    private FotoEnsaio criarFoto(Map opcoes = [:]) {
        def id = UUID.randomUUID()
        def caminhos = [
            originalPath           : "${agendamentoId}/orig/orig_${id}.jpg",
            watermarkedPath        : "${agendamentoId}/orig/wm_orig_${id}.jpg",
            thumbPath              : "${agendamentoId}/orig/thumb_orig_${id}.jpg",
            previewPath            : "${agendamentoId}/orig/preview_v3_orig_${id}.jpg",
            editadaPath            : "${agendamentoId}/editadas/editadas_${id}.jpg",
            editadaWatermarkedPath : "${agendamentoId}/editadas/wm_editadas_${id}.jpg",
            editadaThumbPath       : "${agendamentoId}/editadas/thumb_editadas_${id}.jpg",
            editadaPreviewPath     : "${agendamentoId}/editadas/preview_v3_editadas_${id}.jpg",
        ]
        caminhos.values().each { escrever(it, 10) }
        escrever("${agendamentoId}/preview_v3_${id}_ORIGINAL.jpg", 5)
        escrever("${agendamentoId}/preview_v5_${id}_ORIGINAL.jpg", 6)
        escrever("${agendamentoId}/preview_v6_protegida_${id}_ORIGINAL.jpg", 7)
        escrever("${agendamentoId}/preview_v6_equilibrada_${id}_ORIGINAL.jpg", 8)
        escrever("${agendamentoId}/thumb_protegido_${id}_ORIGINAL.jpg", 4)
        escrever("${agendamentoId}/thumb_protegido_v2_${id}_ORIGINAL.jpg", 4)
        escrever("${agendamentoId}/orig/hero_${id}_ORIGINAL.jpg", 3)

        FotoEnsaio.builder()
            .id(id)
            .agendamentoId(agendamentoId)
            .fileName("IMG_${id}.jpg")
            .originalPath(caminhos.originalPath)
            .watermarkedPath(caminhos.watermarkedPath)
            .thumbPath(caminhos.thumbPath)
            .previewPath(caminhos.previewPath)
            .editadaPath(caminhos.editadaPath)
            .editadaWatermarkedPath(caminhos.editadaWatermarkedPath)
            .editadaThumbPath(caminhos.editadaThumbPath)
            .editadaPreviewPath(caminhos.editadaPreviewPath)
            .status((StatusFoto) opcoes.getOrDefault('status', StatusFoto.PUBLICADA))
            .visivel(true)
            .build()
    }

    private static long bytesDaFoto() {
        return 8 * 10 + 5 + 6 + 7 + 8 + 4 + 4 + 3
    }

    private Path escrever(String relativo, int bytes) {
        def arquivo = uploadDir.resolve(relativo)
        Files.createDirectories(arquivo.parent)
        Files.write(arquivo, new byte[bytes])
        return arquivo
    }

    private static void apagar(Path dir) {
        if (!Files.exists(dir)) {
            return
        }
        Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }
}
