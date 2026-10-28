package com.photoizer.crm.foto.service

import com.photoizer.crm.config.service.MarcaDaguaService
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
    def marcaDaguaService = Mock(MarcaDaguaService)

    @Subject
    def service = new FotoPreviewService(pathResolver, imageProcessingService, marcaDaguaService)

    def agendamentoId = UUID.randomUUID()
    Path dir

    def setup() {
        dir = Files.createTempDirectory("foto-preview-spec")
        marcaDaguaService.texto() >> "© Estúdio"
        marcaDaguaService.opacidade() >> 0.35f
    }

    def cleanup() {
        dir?.toFile()?.deleteDir()
    }

    def "usa a prévia versionada gerada no upload"() {
        given:
        def foto = foto(previewPath: "ag/preview_v5_orig_1.jpg")

        when:
        def caminho = service.caminhoPreview(foto)

        then:
        caminho == "ag/preview_v5_orig_1.jpg"
        0 * imageProcessingService.aplicarMarcaDaguaEm(_, _, _, _, _, _)
        0 * imageProcessingService.redimensionarComMarcaExistente(_, _, _, _)
    }

    def "gera prévia full-res a partir do original quando disponível"() {
        given:
        def original = arquivo("orig_1.jpg")
        def foto = foto()
        pathResolver.resolver("ag/orig.jpg") >> original
        pathResolver.relativo(_) >> { Path p -> p.toString() }
        imageProcessingService.aplicarMarcaDaguaEm(_, _, _, _, _, _) >> { args -> args[1] }

        when:
        def caminho = service.caminhoPreview(foto)

        then:
        caminho.endsWith("preview_v5_${foto.id}_ORIGINAL.jpg")
        1 * imageProcessingService.aplicarMarcaDaguaEm(
            original, _ as Path,
            "© Estúdio",
            0.35f,
            ImageProcessingService.PREVIEW_MAX_DIM,
            ImageProcessingService.PREVIEW_QUALITY)
    }

    def "usa o watermark como fallback quando o original não está disponível"() {
        given:
        def wm = arquivo("wm.jpg")
        def foto = foto(previewPath: "ag/preview_v4_orig_1.jpg")
        pathResolver.resolver("ag/wm.jpg") >> wm
        pathResolver.relativo(_) >> { Path p -> p.toString() }
        imageProcessingService.redimensionarComMarcaExistente(_, _, _, _) >> { args -> args[1] }

        when:
        def caminho = service.caminhoPreview(foto)

        then:
        caminho.endsWith("preview_v5_${foto.id}_ORIGINAL.jpg")
        1 * imageProcessingService.redimensionarComMarcaExistente(
            wm, _ as Path,
            ImageProcessingService.WATERMARK_MAX_DIM,
            ImageProcessingService.PREVIEW_QUALITY)
    }

    def "usa o watermark da edição quando a versão atual é editada"() {
        given:
        def wm = arquivo("editada_wm.jpg")
        def foto = foto(
            versaoAtual: VersaoFoto.EDITADA,
            editadaWatermarkedPath: "ag/editada_wm.jpg",
            editadaPreviewPath: "ag/preview_v4_orig_editada.jpg")
        pathResolver.resolver("ag/editada_wm.jpg") >> wm
        pathResolver.relativo(_) >> { Path p -> p.toString() }
        imageProcessingService.redimensionarComMarcaExistente(_, _, _, _) >> { args -> args[1] }

        when:
        def caminho = service.caminhoPreview(foto)

        then:
        caminho.endsWith("preview_v5_${foto.id}_EDITADA.jpg")
        1 * imageProcessingService.redimensionarComMarcaExistente(
            wm, _ as Path,
            ImageProcessingService.WATERMARK_MAX_DIM,
            ImageProcessingService.PREVIEW_QUALITY)
    }

    def "reaproveita a prévia versionada já gerada sob demanda"() {
        given:
        def wm = arquivo("wm.jpg")
        def foto = foto()
        def alvo = wm.parent.resolve("preview_v5_${foto.id}_ORIGINAL.jpg")
        Files.write(alvo, [1, 2, 3] as byte[])
        pathResolver.resolver("ag/orig.jpg") >> wm.parent.resolve("orig_1.jpg")
        pathResolver.relativo(alvo) >> alvo.toString()

        when:
        def caminho = service.caminhoPreview(foto)

        then:
        caminho == alvo.toString()
        0 * imageProcessingService.aplicarMarcaDaguaEm(_, _, _, _, _, _)
        0 * imageProcessingService.redimensionarComMarcaExistente(_, _, _, _)
    }

    def "gera thumb público 600x400 com cache versionado"() {
        given:
        def wm = arquivo("wm.jpg")
        def foto = foto()
        pathResolver.resolver("ag/wm.jpg") >> wm
        pathResolver.relativo(_) >> { Path p -> p.toString() }
        imageProcessingService.gerarThumbnailProtegido(_, _, _, _, _) >> { args -> args[1] }

        when:
        def caminho = service.caminhoThumb(foto)

        then:
        caminho.endsWith("thumb_protegido_v2_${foto.id}_ORIGINAL.jpg")
        1 * imageProcessingService.gerarThumbnailProtegido(
            wm, _ as Path,
            ImageProcessingService.PUBLIC_THUMB_WIDTH,
            ImageProcessingService.PUBLIC_THUMB_HEIGHT,
            ImageProcessingService.PUBLIC_THUMB_QUALITY)
    }

    def "cai na prévia histórica quando o watermark não está disponível"() {
        given:
        def foto = foto(previewPath: "ag/preview_orig_1.jpg")
        pathResolver.resolver("ag/wm.jpg") >> dir.resolve("inexistente.jpg")

        when:
        def caminho = service.caminhoPreview(foto)

        then:
        caminho == "ag/preview_orig_1.jpg"
        0 * imageProcessingService.aplicarMarcaDaguaEm(_, _, _, _, _, _)
        0 * imageProcessingService.redimensionarComMarcaExistente(_, _, _, _)
    }

    def "prévia pública usa resolução original e qualidade alta"() {
        expect:
        ImageProcessingService.PREVIEW_MAX_DIM == 0
        ImageProcessingService.PREVIEW_QUALITY == 0.92f
        ImageProcessingService.PREVIEW_VERSION == "v5"
        ImageProcessingService.PUBLIC_THUMB_WIDTH == 600
        ImageProcessingService.PUBLIC_THUMB_HEIGHT == 400
        ImageProcessingService.PUBLIC_THUMB_VERSION == "v2"
    }

    def "remove caches versionados na raiz e nos subdiretórios orig/editadas"() {
        given:
        def foto = foto()
        def pastaGaleria = dir.resolve(agendamentoId.toString())
        def orig = Files.createDirectories(pastaGaleria.resolve("orig"))
        def editadas = Files.createDirectories(pastaGaleria.resolve("editadas"))
        def previewOrig = orig.resolve("preview_v5_${foto.id}_ORIGINAL.jpg".toString())
        def thumbEditada = editadas.resolve("thumb_protegido_v2_${foto.id}_EDITADA.jpg".toString())
        Files.write(previewOrig, [1, 2, 3] as byte[])
        Files.write(thumbEditada, [1, 2, 3] as byte[])
        pathResolver.getUploadDir() >> dir

        when:
        service.removerLegados(agendamentoId, [foto.id])

        then:
        !Files.exists(previewOrig)
        !Files.exists(thumbEditada)
    }

    def "remove o cache de prévia de versão anterior"() {
        given:
        def antiga = arquivo("preview_v4_${UUID.randomUUID()}_ORIGINAL.jpg")
        pathResolver.resolver("ag/${antiga.fileName}") >> antiga

        when:
        service.removerPreviewAntiga("ag/${antiga.fileName}")

        then:
        !Files.exists(antiga)
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
