package com.photoizer.crm.foto.api

import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.model.StatusFoto
import com.photoizer.crm.foto.model.VersaoFoto
import com.photoizer.crm.foto.service.FotoPreviewService
import com.photoizer.crm.foto.service.FotoService
import com.photoizer.crm.shared.storage.StoragePathResolver
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Files
import java.nio.file.Path

class FotoControllerSpec extends Specification {

    def fotoService = Mock(FotoService)
    def fotoMapper = Mock(FotoMapper)
    def fotoPreviewService = Mock(FotoPreviewService)
    def pathResolver = Mock(StoragePathResolver)

    @Subject
    def controller = new FotoController(
        fotoService, fotoMapper, fotoPreviewService, pathResolver)

    def agendamentoId = UUID.randomUUID()
    Path dir

    def setup() {
        dir = Files.createTempDirectory("foto-controller-spec")
    }

    def cleanup() {
        dir?.toFile()?.deleteDir()
    }

    def "serve o thumb protegido da galeria para fotos inéditas"() {
        given:
        def foto = foto(StatusFoto.INEDITA)
        def arquivo = arquivo("thumb_protegido_v2_${foto.id}_ORIGINAL.jpg")
        fotoService.buscarParaArquivo(agendamentoId, foto.id) >> foto
        fotoPreviewService.caminhoThumb(foto) >> "ag/${arquivo.fileName}"
        pathResolver.resolver("ag/${arquivo.fileName}") >> arquivo

        when:
        def resposta = controller.servirThumb(agendamentoId, foto.id)

        then:
        resposta.statusCode.value() == 200
        resposta.headers.contentType == MediaType.IMAGE_JPEG
        resposta.body instanceof FileSystemResource
        resposta.body.path == arquivo.toString()
    }

    def "usa o thumb interno como fallback quando o protegido não está disponível"() {
        given:
        def foto = foto(StatusFoto.PUBLICADA)
        def arquivo = arquivo("thumb_${foto.id}.jpg")
        foto.thumbPath = "ag/${arquivo.fileName}"
        fotoService.buscarParaArquivo(agendamentoId, foto.id) >> foto
        fotoPreviewService.caminhoThumb(foto) >> null
        pathResolver.resolver(foto.thumbPath) >> arquivo

        when:
        def resposta = controller.servirThumb(agendamentoId, foto.id)

        then:
        resposta.statusCode.value() == 200
        resposta.headers.contentType == MediaType.IMAGE_JPEG
        resposta.body.path == arquivo.toString()
    }

    def "responde 404 quando a foto não tem nenhum thumb"() {
        given:
        def foto = foto(StatusFoto.PUBLICADA)
        foto.thumbPath = null
        fotoService.buscarParaArquivo(agendamentoId, foto.id) >> foto
        fotoPreviewService.caminhoThumb(foto) >> null

        when:
        def resposta = controller.servirThumb(agendamentoId, foto.id)

        then:
        resposta.statusCode.value() == 404
    }

    private Path arquivo(String nome) {
        def caminho = dir.resolve(nome)
        Files.write(caminho, [1, 2, 3] as byte[])
        return caminho
    }

    private FotoEnsaio foto(StatusFoto status) {
        return FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .fileName("IMG_1.jpg")
            .originalPath("ag/orig.jpg")
            .watermarkedPath("ag/wm.jpg")
            .thumbPath("ag/thumb_${UUID.randomUUID()}.jpg")
            .versaoAtual(VersaoFoto.ORIGINAL)
            .status(status)
            .visivel(true)
            .build()
    }
}
