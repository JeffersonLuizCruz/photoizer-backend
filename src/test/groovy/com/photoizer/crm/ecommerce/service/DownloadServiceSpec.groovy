package com.photoizer.crm.ecommerce.service

import com.photoizer.crm.agenda.model.Agendamento
import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.model.StatusFoto
import com.photoizer.crm.foto.model.VersaoFoto
import com.photoizer.crm.foto.repository.FotoEnsaioRepository
import com.photoizer.crm.shared.storage.StoragePathResolver
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Files
import java.nio.file.Path

class DownloadServiceSpec extends Specification {

    def fotoRepository = Mock(FotoEnsaioRepository)
    def galeriaQueryService = Mock(GaleriaQueryService)
    def eventPublisher = Mock(ApplicationEventPublisher)
    def pathResolver = Mock(StoragePathResolver)

    @Subject
    def service = new DownloadService(
        fotoRepository, galeriaQueryService, eventPublisher, pathResolver)

    def token = UUID.randomUUID()
    def agendamentoId = UUID.randomUUID()
    Path dir

    def setup() {
        dir = Files.createTempDirectory("download-spec")
    }

    def cleanup() {
        dir?.toFile()?.deleteDir()
    }

    def "baixa o original mesmo quando existem preview e watermark"() {
        given:
        def agendamento = Agendamento.builder().id(agendamentoId).build()
        def original = arquivo("orig_1.jpg")
        def preview = arquivo("preview_v5_1_ORIGINAL.jpg")
        def watermarked = arquivo("wm_orig_1.jpg")
        def foto = foto(original, preview, watermarked)
        galeriaQueryService.buscarAgendamentoPorToken(token) >> agendamento
        fotoRepository.findById(foto.id) >> Optional.of(foto)
        galeriaQueryService.isDownloadPermitido(foto) >> true
        pathResolver.resolver(foto.originalPath) >> original

        when:
        def resultado = service.downloadFoto(token, foto.id)

        then:
        resultado.path() == original
        resultado.nome() == "001_IMG_1.jpg"
        0 * pathResolver.resolver(foto.previewPath)
        0 * pathResolver.resolver(foto.watermarkedPath)
        1 * eventPublisher.publishEvent(_)
    }

    def "baixa a versão editada quando a versão atual é EDITADA"() {
        given:
        def agendamento = Agendamento.builder().id(agendamentoId).build()
        def original = arquivo("orig_1.jpg")
        def editada = arquivo("editada_1.jpg")
        def preview = arquivo("preview_v5_1_EDITADA.jpg")
        def watermarked = arquivo("wm_editada_1.jpg")
        def foto = foto(original, preview, watermarked, VersaoFoto.EDITADA, editada)
        galeriaQueryService.buscarAgendamentoPorToken(token) >> agendamento
        fotoRepository.findById(foto.id) >> Optional.of(foto)
        galeriaQueryService.isDownloadPermitido(foto) >> true
        pathResolver.resolver(foto.editadaPath) >> editada

        when:
        def resultado = service.downloadFoto(token, foto.id)

        then:
        resultado.path() == editada
        0 * pathResolver.resolver(foto.originalPath)
        0 * pathResolver.resolver(foto.previewPath)
        0 * pathResolver.resolver(foto.watermarkedPath)
    }

    private Path arquivo(String nome) {
        def caminho = dir.resolve(nome)
        Files.write(caminho, [1, 2, 3] as byte[])
        return caminho
    }

    private FotoEnsaio foto(Path original, Path preview, Path watermarked,
                            VersaoFoto versao = VersaoFoto.ORIGINAL,
                            Path editada = null) {
        return FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .fileName("IMG_1.jpg")
            .originalPath(original.toString())
            .previewPath(preview.toString())
            .watermarkedPath(watermarked.toString())
            .editadaPath(editada?.toString())
            .versaoAtual(versao)
            .numeroGaleria(1)
            .ordem(1)
            .status(StatusFoto.PAGA)
            .visivel(true)
            .build()
    }
}
