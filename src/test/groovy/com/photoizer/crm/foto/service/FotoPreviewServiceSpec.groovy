package com.photoizer.crm.foto.service

import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.model.StatusFoto
import com.photoizer.crm.foto.model.VersaoFoto
import com.photoizer.crm.shared.processing.ImageProcessingService
import com.photoizer.crm.shared.storage.StoragePathResolver
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Files
import java.nio.file.Path

class FotoPreviewServiceSpec extends Specification {

    def pathResolver = Mock(StoragePathResolver)
    def imageProcessingService = Mock(ImageProcessingService)

    @Subject
    def service = new FotoPreviewService(pathResolver, imageProcessingService)

    def agendamentoId = UUID.randomUUID()
    Path dir

    def setup() {
        dir = Files.createTempDirectory("foto-preview-spec")
    }

    def cleanup() {
        dir?.toFile()?.deleteDir()
    }

    def "usa a prévia versionada gerada no upload"() {
        given:
        def foto = foto(previewPath: "ag/preview_v4_orig_1.jpg")

        when:
        def caminho = service.caminhoPreview(foto)

        then:
        caminho == "ag/preview_v4_orig_1.jpg"
        0 * imageProcessingService.redimensionarComMarcaExistente(_, _, _, _)
    }

    def "gera prévia 800px a partir do watermark quando o cache é antigo"() {
        given:
        def wm = arquivo("wm.jpg")
        def foto = foto(previewPath: "ag/preview_orig_1.jpg")
        pathResolver.resolver("ag/wm.jpg") >> wm
        pathResolver.relativo(_) >> { Path p -> p.toString() }
        imageProcessingService.redimensionarComMarcaExistente(_, _, _, _) >> { args -> args[1] }

        when:
        def caminho = service.caminhoPreview(foto)

        then:
        caminho.endsWith("preview_v4_${foto.id}_ORIGINAL.jpg")
        1 * imageProcessingService.redimensionarComMarcaExistente(
            wm, _ as Path,
            ImageProcessingService.PREVIEW_MAX_DIM,
            ImageProcessingService.PREVIEW_QUALITY)
    }

    def "usa o watermark da edição quando a versão atual é editada"() {
        given:
        def wm = arquivo("editada_wm.jpg")
        def foto = foto(
            versaoAtual: VersaoFoto.EDITADA,
            editadaWatermarkedPath: "ag/editada_wm.jpg",
            editadaPreviewPath: "ag/preview_orig_editada.jpg")
        pathResolver.resolver("ag/editada_wm.jpg") >> wm
        pathResolver.relativo(_) >> { Path p -> p.toString() }
        imageProcessingService.redimensionarComMarcaExistente(_, _, _, _) >> { args -> args[1] }

        when:
        def caminho = service.caminhoPreview(foto)

        then:
        caminho.endsWith("preview_v4_${foto.id}_EDITADA.jpg")
        1 * imageProcessingService.redimensionarComMarcaExistente(
            wm, _ as Path,
            ImageProcessingService.PREVIEW_MAX_DIM,
            ImageProcessingService.PREVIEW_QUALITY)
    }

    def "reaproveita a prévia versionada já gerada sob demanda"() {
        given:
        def wm = arquivo("wm.jpg")
        def foto = foto()
        def alvo = wm.parent.resolve("preview_v4_${foto.id}_ORIGINAL.jpg")
        Files.write(alvo, [1, 2, 3] as byte[])
        pathResolver.resolver("ag/wm.jpg") >> wm
        pathResolver.relativo(alvo) >> alvo.toString()

        when:
        def caminho = service.caminhoPreview(foto)

        then:
        caminho == alvo.toString()
        0 * imageProcessingService.redimensionarComMarcaExistente(_, _, _, _)
    }

    def "cai na prévia histórica quando o watermark não está disponível"() {
        given:
        def foto = foto(previewPath: "ag/preview_orig_1.jpg")
        pathResolver.resolver("ag/wm.jpg") >> dir.resolve("inexistente.jpg")

        when:
        def caminho = service.caminhoPreview(foto)

        then:
        caminho == "ag/preview_orig_1.jpg"
        0 * imageProcessingService.redimensionarComMarcaExistente(_, _, _, _)
    }

    def "prévia pública usa a qualidade reduzida configurada"() {
        expect:
        ImageProcessingService.PREVIEW_MAX_DIM == 800
        ImageProcessingService.PREVIEW_QUALITY == 0.60f
        ImageProcessingService.PREVIEW_VERSION == "v4"
    }

    private Path arquivo(String nome) {
        def caminho = dir.resolve(nome)
        Files.write(caminho, [1, 2, 3] as byte[])
        return caminho
    }

    private FotoEnsaio foto(Map overrides = [:]) {
        def base = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .fileName("IMG_1.jpg")
            .originalPath("ag/orig.jpg")
            .watermarkedPath("ag/wm.jpg")
            .thumbPath("ag/thumb.jpg")
            .versaoAtual(VersaoFoto.ORIGINAL)
            .status(StatusFoto.PUBLICADA)
            .visivel(true)
            .build()
        overrides.each { chave, valor -> base."$chave" = valor }
        return base
    }
}
