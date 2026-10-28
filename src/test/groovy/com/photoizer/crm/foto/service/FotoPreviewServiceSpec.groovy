package com.photoizer.crm.foto.service

import com.photoizer.crm.config.service.MarcaDaguaService
import com.photoizer.crm.foto.acl.GaleriaQualidadePreviewReadService
import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.model.StatusFoto
import com.photoizer.crm.foto.model.VersaoFoto
import com.photoizer.crm.shared.processing.ImageProcessingService
import com.photoizer.crm.shared.processing.QualidadePreview
import com.photoizer.crm.shared.storage.StoragePathResolver
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Files
import java.nio.file.Path

class FotoPreviewServiceSpec extends Specification {

    def pathResolver = Mock(StoragePathResolver)
    def imageProcessingService = Mock(ImageProcessingService)
    def marcaDaguaService = Mock(MarcaDaguaService)
    def qualidadeReadService = Mock(GaleriaQualidadePreviewReadService)

    @Subject
    def service = new FotoPreviewService(
        pathResolver, imageProcessingService, marcaDaguaService, qualidadeReadService)

    def agendamentoId = UUID.randomUUID()
    Path dir

    def setup() {
        dir = Files.createTempDirectory("foto-preview-spec")
        marcaDaguaService.texto() >> "© Estúdio"
        marcaDaguaService.opacidade() >> 0.35f
        qualidadeReadService.qualidadePreview(agendamentoId) >> QualidadePreview.ORIGINAL
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

    def "gera prévia full-res a partir do original na qualidade original"() {
        given:
        def original = arquivo("orig_1.jpg")
        def foto = foto()
        pathResolver.resolver("ag/orig.jpg") >> original
        pathResolver.relativo(_) >> { Path p -> p.toString() }
        imageProcessingService.aplicarMarcaDaguaEm(_, _, _, _, _, _) >> { args -> args[1] }

        when:
        def caminho = service.caminhoPreview(foto)

        then:
        caminho.endsWith("${QualidadePreview.ORIGINAL.prefixoCache}${foto.id}_ORIGINAL.jpg")
        1 * imageProcessingService.aplicarMarcaDaguaEm(
            original, _ as Path,
            "© Estúdio",
            0.35f,
            QualidadePreview.ORIGINAL.maxDim,
            QualidadePreview.ORIGINAL.qualidade)
    }

    def "gera prévia 800px quando a galeria usa a qualidade protegida"() {
        given:
        def outroAgendamento = UUID.randomUUID()
        qualidadeReadService.qualidadePreview(outroAgendamento) >> QualidadePreview.PROTEGIDA
        def original = arquivo("orig_1.jpg")
        def foto = foto(agendamentoId: outroAgendamento)
        pathResolver.resolver("ag/orig.jpg") >> original
        pathResolver.relativo(_) >> { Path p -> p.toString() }
        imageProcessingService.aplicarMarcaDaguaEm(_, _, _, _, _, _) >> { args -> args[1] }

        when:
        def caminho = service.caminhoPreview(foto)

        then:
        caminho.endsWith("${QualidadePreview.PROTEGIDA.prefixoCache}${foto.id}_ORIGINAL.jpg")
        1 * imageProcessingService.aplicarMarcaDaguaEm(
            original, _ as Path,
            "© Estúdio",
            0.35f,
            QualidadePreview.PROTEGIDA.maxDim,
            QualidadePreview.PROTEGIDA.qualidade)
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
        caminho.endsWith("${QualidadePreview.ORIGINAL.prefixoCache}${foto.id}_ORIGINAL.jpg")
        1 * imageProcessingService.redimensionarComMarcaExistente(
            wm, _ as Path,
            QualidadePreview.ORIGINAL.maxDim,
            QualidadePreview.ORIGINAL.qualidade)
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
        caminho.endsWith("${QualidadePreview.ORIGINAL.prefixoCache}${foto.id}_EDITADA.jpg")
        1 * imageProcessingService.redimensionarComMarcaExistente(
            wm, _ as Path,
            QualidadePreview.ORIGINAL.maxDim,
            QualidadePreview.ORIGINAL.qualidade)
    }

    def "reaproveita a prévia versionada já gerada sob demanda"() {
        given:
        def wm = arquivo("wm.jpg")
        def foto = foto()
        def alvo = wm.parent.resolve("${QualidadePreview.ORIGINAL.prefixoCache}${foto.id}_ORIGINAL.jpg".toString())
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

    def "presets de qualidade mantêm a resolução e a qualidade esperadas"() {
        expect:
        QualidadePreview.PROTEGIDA.maxDim == 800
        QualidadePreview.PROTEGIDA.qualidade == 0.60f
        QualidadePreview.EQUILIBRADA.maxDim == 1600
        QualidadePreview.EQUILIBRADA.qualidade == 0.90f
        QualidadePreview.ORIGINAL.maxDim == 0
        QualidadePreview.ORIGINAL.qualidade == 0.92f
        QualidadePreview.padrao() == QualidadePreview.ORIGINAL
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

    def "remove caches das outras qualidades e preserva a atual"() {
        given:
        def foto = foto()
        def orig = Files.createDirectories(dir.resolve(agendamentoId.toString()).resolve("orig"))
        def antiga = orig.resolve("preview_v6_protegida_${foto.id}_ORIGINAL.jpg".toString())
        def atual = orig.resolve("preview_v5_${foto.id}_ORIGINAL.jpg".toString())
        Files.write(antiga, [1, 2, 3] as byte[])
        Files.write(atual, [1, 2, 3] as byte[])
        pathResolver.getUploadDir() >> dir

        when:
        service.removerCachesDeOutrasQualidades(
            agendamentoId, foto.id, VersaoFoto.ORIGINAL, QualidadePreview.ORIGINAL)

        then:
        !Files.exists(antiga)
        Files.exists(atual)
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
