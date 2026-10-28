package com.photoizer.crm.foto.service

import com.photoizer.crm.foto.acl.AgendamentoReadService
import com.photoizer.crm.foto.acl.GaleriaQualidadePreviewReadService
import com.photoizer.crm.foto.event.FotoReeditadaEvent
import com.photoizer.crm.foto.event.FotosRemovidasEvent
import com.photoizer.crm.foto.exception.ArquivoDeImagemInvalidoException
import com.photoizer.crm.foto.exception.ArquivoExpurgadoException
import com.photoizer.crm.foto.exception.FotoEnsaioNaoEncontradaException
import com.photoizer.crm.foto.exception.FotoNaoEditavelException
import com.photoizer.crm.foto.exception.FotoNaoPertenceAoAgendamentoException
import com.photoizer.crm.foto.exception.FotoVinculadaACompraException
import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.model.StatusFoto
import com.photoizer.crm.foto.model.VersaoFoto
import com.photoizer.crm.foto.repository.FotoEnsaioRepository
import com.photoizer.crm.shared.processing.QualidadePreview
import com.photoizer.crm.shared.storage.FileStorageService
import com.photoizer.crm.shared.storage.FileValidator
import com.photoizer.crm.shared.storage.StoragePathResolver
import org.springframework.context.ApplicationEventPublisher
import org.springframework.mock.web.MockMultipartFile
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class FotoServiceSpec extends Specification {

    def agendamentoId = UUID.randomUUID()
    def fotoRepository = Mock(FotoEnsaioRepository)
    def fileStorageService = Mock(FileStorageService)
    def processingHelper = Mock(FotoProcessingHelper)
    def previewService = Mock(FotoPreviewService)
    def agendamentoReadService = Mock(AgendamentoReadService)
    def qualidadeReadService = Mock(GaleriaQualidadePreviewReadService)
    def fileValidator = Mock(FileValidator)
    def pathResolver = Mock(StoragePathResolver)
    def eventPublisher = Mock(ApplicationEventPublisher)

    @Subject
    def service = new FotoService(
        fotoRepository,
        fileStorageService,
        processingHelper,
        previewService,
        agendamentoReadService,
        qualidadeReadService,
        fileValidator,
        pathResolver,
        eventPublisher
    )

    def setup() {
        qualidadeReadService.qualidadePreview(agendamentoId) >> QualidadePreview.ORIGINAL
    }

    def "rejeita imagem ilegivel e remove o arquivo gravado"() {
        given:
        def dir = Files.createTempDirectory("foto-service-spec")
        def quebrada = dir.resolve("orig_quebrada.jpg")
        Files.write(quebrada, "isto-nao-e-uma-imagem".bytes)
        agendamentoReadService.isStatusPermitidoParaUpload(agendamentoId) >> true
        fotoRepository.findMaxOrdemByAgendamentoId(agendamentoId) >> -1
        fileStorageService.salvarEmSubdiretorio(_, agendamentoId, "orig") >> quebrada.toString()
        pathResolver.resolver(quebrada.toString()) >> quebrada
        def arquivo = new MockMultipartFile(
            "arquivos", "quebrada.jpg", "image/jpeg", "isto-nao-e-uma-imagem".bytes)

        when:
        service.uploadFotos(agendamentoId, [arquivo])

        then:
        def erro = thrown(ArquivoDeImagemInvalidoException)
        erro.message.contains("quebrada.jpg")
        !Files.exists(quebrada)
        0 * fotoRepository.save(_ as FotoEnsaio)

        cleanup:
        dir.toFile().deleteDir()
    }

    def "primeira versão editada não falha quando ainda não existem caminhos antigos"() {
        given:
        def tmp = Files.createTempDirectory("foto-editada-spec")
        def editada = tmp.resolve("editada.png")
        def bytes = novoArquivoPng("editada.png").bytes
        Files.write(editada, bytes)
        def watermarked = tmp.resolve("wm_editada.jpg")
        def thumb = tmp.resolve("thumb_editada.jpg")
        def preview = tmp.resolve("preview_editada.jpg")
        Files.write(watermarked, bytes)
        Files.write(thumb, bytes)
        Files.write(preview, bytes)
        def foto = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .fileName("IMG_1.png")
            .originalPath("orig/1.png")
            .watermarkedPath("wm/1.png")
            .thumbPath("thumb/1.png")
            .status(StatusFoto.PUBLICADA)
            .visivel(true)
            .build()
        agendamentoReadService.isStatusPermitidoParaUpload(agendamentoId) >> true
        fotoRepository.findById(foto.id) >> Optional.of(foto)
        fileStorageService.salvarEmSubdiretorio(_, agendamentoId, "editadas") >> editada.toString()
        pathResolver.resolver(editada.toString()) >> editada
        processingHelper.processar(editada, editada.parent, foto.id, QualidadePreview.ORIGINAL) >>
            new FotoProcessingHelper.ProcessedImages(watermarked.toString(), thumb.toString(), preview.toString())
        pathResolver.relativo(Path.of(watermarked.toString())) >> "editadas/wm.jpg"
        pathResolver.relativo(Path.of(thumb.toString())) >> "editadas/thumb.jpg"
        pathResolver.relativo(Path.of(preview.toString())) >> "editadas/preview.jpg"
        fileValidator.sanitizeFilename("editada.png") >> "editada.png"
        fotoRepository.save(_ as FotoEnsaio) >> { args -> args[0] }

        when:
        def salva = service.uploadEditada(agendamentoId, foto.id, novoArquivoPng("editada.png"))

        then:
        noExceptionThrown()
        salva.editadaPath == editada.toString()
        salva.editadaFileName == "editada.png"
        salva.editadaEm != null
        salva.editadaPreviewPath == "editadas/preview.jpg"

        cleanup:
        tmp.toFile().deleteDir()
    }

    def "remove arquivos gravados quando o processamento falha"() {
        given:
        def tmp = arquivosDeTeste(1)
        def original = tmp.originais[0]
        agendamentoReadService.isStatusPermitidoParaUpload(agendamentoId) >> true
        fotoRepository.findMaxOrdemByAgendamentoId(agendamentoId) >> -1
        fileStorageService.salvarEmSubdiretorio(_, agendamentoId, "orig") >> original.toString()
        pathResolver.resolver(original.toString()) >> original
        processingHelper.processar(original, original.parent, _ as UUID, QualidadePreview.ORIGINAL) >> {
            throw new IllegalStateException("falha no processamento")
        }

        when:
        service.uploadFotos(agendamentoId, [novoArquivoPng("foto.png")])

        then:
        thrown(IllegalStateException)
        !Files.exists(original)
        0 * fotoRepository.save(_ as FotoEnsaio)

        cleanup:
        tmp.dir.toFile().deleteDir()
    }

    def "converte imagem corrompida em 422 e remove o arquivo gravado"() {
        given:
        def tmp = arquivosDeTeste(1)
        def original = tmp.originais[0]
        agendamentoReadService.isStatusPermitidoParaUpload(agendamentoId) >> true
        fotoRepository.findMaxOrdemByAgendamentoId(agendamentoId) >> -1
        fileStorageService.salvarEmSubdiretorio(_, agendamentoId, "orig") >> original.toString()
        pathResolver.resolver(original.toString()) >> original
        processingHelper.processar(original, original.parent, _ as UUID, QualidadePreview.ORIGINAL) >> {
            throw new IllegalArgumentException("Não foi possível decodificar a imagem")
        }

        when:
        service.uploadFotos(agendamentoId, [novoArquivoPng("corrompida.png")])

        then:
        def erro = thrown(ArquivoDeImagemInvalidoException)
        erro.message.contains("corrompida.png")
        !Files.exists(original)
        0 * fotoRepository.save(_ as FotoEnsaio)

        cleanup:
        tmp.dir.toFile().deleteDir()
    }

    def "numera a ordem ao enviar lote valido e mantem os arquivos"() {
        given:
        def tmp = arquivosDeTeste(2)
        def primeira = tmp.originais[0]
        def segunda = tmp.originais[1]
        def watermark = tmp.dir.resolve("wm.png")
        def thumb = tmp.dir.resolve("thumb.png")
        def preview = tmp.dir.resolve("preview.png")

        agendamentoReadService.isStatusPermitidoParaUpload(agendamentoId) >> true
        fotoRepository.findMaxOrdemByAgendamentoId(agendamentoId) >> 4
        fileStorageService.salvarEmSubdiretorio(_, agendamentoId, "orig") >>> [
            primeira.toString(),
            segunda.toString()
        ]
        pathResolver.resolver(_ as String) >> { String caminho ->
            tmp.originais.find { it.toString() == caminho }
        }
        processingHelper.processar(primeira, tmp.dir, _ as UUID, QualidadePreview.ORIGINAL) >>
            new FotoProcessingHelper.ProcessedImages(watermark.toString(), thumb.toString(), preview.toString())
        processingHelper.processar(segunda, tmp.dir, _ as UUID, QualidadePreview.ORIGINAL) >>
            new FotoProcessingHelper.ProcessedImages(watermark.toString(), thumb.toString(), preview.toString())
        pathResolver.relativo(_ as Path) >> "agendamento/foto.jpg"

        when:
        def fotos = service.uploadFotos(agendamentoId, [
            novoArquivoPng("primeira.png"),
            novoArquivoPng("segunda.png")
        ])

        then:
        fotos*.ordem == [5, 6]
        fotos*.status == [StatusFoto.INEDITA, StatusFoto.INEDITA]
        2 * fotoRepository.save(_ as FotoEnsaio) >> { args -> args[0] }

        cleanup:
        tmp.dir.toFile().deleteDir()
    }

    def "remove fotos em lote e apaga os arquivos"() {
        given:
        def tmp = Files.createTempDirectory("foto-service-spec")
        def primeira = fotoComArquivos(tmp)
        def segunda = fotoComArquivos(tmp)
        fotoRepository.findAllById(_ as Iterable) >> [primeira, segunda]
        pathResolver.resolver(_ as String) >> { String caminho -> Path.of(caminho) }

        when:
        def removidas = service.deletarEmLote(agendamentoId, [primeira.id, segunda.id])

        then:
        removidas == 2
        1 * fotoRepository.deleteAll([primeira, segunda])
        1 * eventPublisher.publishEvent({ it instanceof FotosRemovidasEvent
            && it.agendamentoId() == agendamentoId
            && it.fotoIds().size() == 2 })
        !Files.exists(Path.of(primeira.originalPath))
        !Files.exists(Path.of(primeira.watermarkedPath))
        !Files.exists(Path.of(primeira.thumbPath))
        !Files.exists(Path.of(segunda.originalPath))
        !Files.exists(Path.of(segunda.watermarkedPath))
        !Files.exists(Path.of(segunda.thumbPath))

        cleanup:
        tmp.toFile().deleteDir()
    }

    def "remove apenas uma vez quando o lote tem ids duplicados"() {
        given:
        def tmp = Files.createTempDirectory("foto-service-spec")
        def foto = fotoComArquivos(tmp)
        fotoRepository.findAllById({ it.size() == 1 }) >> [foto]
        pathResolver.resolver(_ as String) >> { String caminho -> Path.of(caminho) }

        when:
        def removidas = service.deletarEmLote(agendamentoId, [foto.id, foto.id])

        then:
        removidas == 1
        1 * fotoRepository.deleteAll([foto])
        1 * eventPublisher.publishEvent({ it instanceof FotosRemovidasEvent && it.fotoIds() == [foto.id] })

        cleanup:
        tmp.toFile().deleteDir()
    }

    def "rejeita lote com id inexistente e nao remove nada"() {
        given:
        def tmp = Files.createTempDirectory("foto-service-spec")
        def foto = fotoComArquivos(tmp)
        def inexistente = UUID.randomUUID()
        fotoRepository.findAllById(_ as Iterable) >> [foto]

        when:
        service.deletarEmLote(agendamentoId, [foto.id, inexistente])

        then:
        def erro = thrown(FotoEnsaioNaoEncontradaException)
        erro.message.contains(inexistente.toString())
        0 * fotoRepository.deleteAll(_ as Iterable)
        0 * eventPublisher.publishEvent(_)
        Files.exists(Path.of(foto.originalPath))

        cleanup:
        tmp.toFile().deleteDir()
    }

    def "rejeita lote com foto de outro agendamento e nao remove nada"() {
        given:
        def tmp = Files.createTempDirectory("foto-service-spec")
        def foto = fotoComArquivos(tmp)
        foto.agendamentoId = UUID.randomUUID()
        fotoRepository.findAllById(_ as Iterable) >> [foto]

        when:
        service.deletarEmLote(agendamentoId, [foto.id])

        then:
        thrown(FotoNaoPertenceAoAgendamentoException)
        0 * fotoRepository.deleteAll(_ as Iterable)
        0 * eventPublisher.publishEvent(_)
        Files.exists(Path.of(foto.originalPath))

        cleanup:
        tmp.toFile().deleteDir()
    }

    @Unroll
    def "bloqueia remocao de foto protegida (#status, pacote=#selecionada, compra=#comCompra)"() {
        given:
        def tmp = Files.createTempDirectory("foto-service-spec")
        def foto = fotoComArquivos(tmp, status, selecionada, comCompra ? UUID.randomUUID() : null)
        fotoRepository.findAllById(_ as Iterable) >> [foto]

        when:
        service.deletarEmLote(agendamentoId, [foto.id])

        then:
        thrown(FotoVinculadaACompraException)
        0 * fotoRepository.deleteAll(_ as Iterable)
        0 * eventPublisher.publishEvent(_)
        Files.exists(Path.of(foto.originalPath))

        cleanup:
        tmp.toFile().deleteDir()

        where:
        status                            | selecionada | comCompra
        StatusFoto.PAGA                   | false       | false
        StatusFoto.AGUARDANDO_COMPROVANTE | false       | false
        StatusFoto.AGUARDANDO_CONFIRMACAO | false       | false
        StatusFoto.PUBLICADA              | true        | false
        StatusFoto.PUBLICADA              | false       | true
    }

    def "envia versão editada de extra comprado e mantém a versão original até liberar"() {
        given:
        def tmp = Files.createTempDirectory("foto-extra-spec")
        def editada = tmp.resolve("editada.png")
        def bytes = novoArquivoPng("editada.png").bytes
        Files.write(editada, bytes)
        def watermarked = tmp.resolve("wm_editada.jpg")
        def thumb = tmp.resolve("thumb_editada.jpg")
        def preview = tmp.resolve("preview_editada.jpg")
        Files.write(watermarked, bytes)
        Files.write(thumb, bytes)
        Files.write(preview, bytes)
        def foto = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .fileName("IMG_1.png")
            .originalPath("orig/1.png")
            .watermarkedPath("wm/1.png")
            .thumbPath("thumb/1.png")
            .status(StatusFoto.PAGA)
            .compraExtraId(UUID.randomUUID())
            .versaoAtual(VersaoFoto.ORIGINAL)
            .visivel(true)
            .build()
        agendamentoReadService.isStatusPermitidoParaUpload(agendamentoId) >> true
        fotoRepository.findById(foto.id) >> Optional.of(foto)
        fileStorageService.salvarEmSubdiretorio(_, agendamentoId, "editadas") >> editada.toString()
        pathResolver.resolver(editada.toString()) >> editada
        processingHelper.processar(editada, editada.parent, foto.id, QualidadePreview.ORIGINAL) >>
            new FotoProcessingHelper.ProcessedImages(watermarked.toString(), thumb.toString(), preview.toString())
        pathResolver.relativo(Path.of(watermarked.toString())) >> "editadas/wm.jpg"
        pathResolver.relativo(Path.of(thumb.toString())) >> "editadas/thumb.jpg"
        pathResolver.relativo(Path.of(preview.toString())) >> "editadas/preview.jpg"
        fileValidator.sanitizeFilename("editada.png") >> "editada.png"
        fotoRepository.save(_ as FotoEnsaio) >> { args -> args[0] }

        when:
        def salva = service.uploadEditadaCompraExtra(
            agendamentoId, foto.id, novoArquivoPng("editada.png"))

        then:
        salva.editadaPath == editada.toString()
        salva.versaoAtual == VersaoFoto.ORIGINAL
        0 * eventPublisher.publishEvent(_)

        cleanup:
        tmp.toFile().deleteDir()
    }

    def "não envia edição de extra antes do pagamento"() {
        given:
        def foto = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .fileName("IMG_1.png")
            .originalPath("orig/1.png")
            .watermarkedPath("wm/1.png")
            .thumbPath("thumb/1.png")
            .status(StatusFoto.PUBLICADA)
            .compraExtraId(UUID.randomUUID())
            .visivel(true)
            .build()
        agendamentoReadService.isStatusPermitidoParaUpload(agendamentoId) >> true
        fotoRepository.findById(foto.id) >> Optional.of(foto)

        when:
        service.uploadEditadaCompraExtra(agendamentoId, foto.id, novoArquivoPng("editada.png"))

        then:
        thrown(FotoNaoEditavelException)
        0 * fileStorageService.salvarEmSubdiretorio(_, _, _)
    }

    def "libera download da edição de extra comprado"() {
        given:
        def foto = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .fileName("IMG_1.png")
            .originalPath("orig/1.png")
            .watermarkedPath("wm/1.png")
            .thumbPath("thumb/1.png")
            .status(StatusFoto.PAGA)
            .compraExtraId(UUID.randomUUID())
            .editadaPath("editadas/1.jpg")
            .versaoAtual(VersaoFoto.ORIGINAL)
            .visivel(true)
            .build()
        fotoRepository.findById(foto.id) >> Optional.of(foto)
        fotoRepository.save(_ as FotoEnsaio) >> { args -> args[0] }

        when:
        def salva = service.publicarEditadaCompraExtra(agendamentoId, foto.id)

        then:
        salva.versaoAtual == VersaoFoto.EDITADA
    }

    def "não libera extra sem versão editada"() {
        given:
        def foto = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .fileName("IMG_1.png")
            .originalPath("orig/1.png")
            .watermarkedPath("wm/1.jpg")
            .thumbPath("thumb/1.jpg")
            .status(StatusFoto.PAGA)
            .compraExtraId(UUID.randomUUID())
            .visivel(true)
            .build()
        fotoRepository.findById(foto.id) >> Optional.of(foto)

        when:
        service.publicarEditadaCompraExtra(agendamentoId, foto.id)

        then:
        thrown(FotoNaoEditavelException)
        0 * fotoRepository.save(_)
    }

    def "aplica a qualidade da prévia e remove os caches antigos"() {
        given:
        def foto = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .fileName("IMG_1.jpg")
            .originalPath("ag/orig.jpg")
            .watermarkedPath("ag/wm.jpg")
            .thumbPath("ag/thumb.jpg")
            .previewPath("ag/preview_v4_orig.jpg")
            .status(StatusFoto.PUBLICADA)
            .visivel(true)
            .build()
        def novo = "ag/preview_v5_${foto.id}_ORIGINAL.jpg".toString()
        fotoRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId) >> [foto]

        when:
        def resultado = service.aplicarQualidadePreview(agendamentoId)

        then:
        1 * previewService.regenerarPreview(foto, QualidadePreview.ORIGINAL) >> Optional.of(novo)
        1 * previewService.caminhoThumb(foto) >> "ag/thumb_protegido_v2_${foto.id}_ORIGINAL.jpg"
        1 * fotoRepository.save(foto) >> foto
        1 * previewService.removerPreviewAntiga("ag/preview_v4_orig.jpg")
        1 * previewService.removerCachesDeOutrasQualidades(
            agendamentoId, foto.id, VersaoFoto.ORIGINAL, QualidadePreview.ORIGINAL)
        resultado.qualidade() == "ORIGINAL"
        resultado.total() == 1
        resultado.geradas() == 1
        resultado.falhas() == 0
        foto.previewPath == novo
    }

    def "regenera a marca d'água do original e da versão editada"() {
        given:
        def tmp = Files.createTempDirectory("foto-regerar-spec")
        def original = tmp.resolve("orig_1.png")
        def editada = tmp.resolve("editada_1.png")
        Files.write(original, novoArquivoPng("orig_1.png").bytes)
        Files.write(editada, novoArquivoPng("editada_1.png").bytes)
        def wmOriginal = tmp.resolve("wm_orig_1.jpg")
        def thumbOriginal = tmp.resolve("thumb_orig_1.jpg")
        def previewOriginal = tmp.resolve("preview_v5_orig_1.jpg")
        def wmEditada = tmp.resolve("wm_editada_1.jpg")
        def thumbEditada = tmp.resolve("thumb_editada_1.jpg")
        def previewEditada = tmp.resolve("preview_v5_editada_1.jpg")
        def foto = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .fileName("orig_1.png")
            .originalPath("orig/1.png")
            .watermarkedPath("orig/wm_antigo.jpg")
            .thumbPath("orig/thumb_antigo.jpg")
            .editadaPath("editadas/1.png")
            .editadaWatermarkedPath("editadas/wm_antigo.jpg")
            .editadaThumbPath("editadas/thumb_antigo.jpg")
            .status(StatusFoto.PUBLICADA)
            .visivel(true)
            .build()
        fotoRepository.findById(foto.id) >> Optional.of(foto)
        pathResolver.resolver(foto.originalPath) >> original
        pathResolver.resolver(foto.editadaPath) >> editada
        processingHelper.processar(original, original.parent, foto.id, QualidadePreview.ORIGINAL) >>
            new FotoProcessingHelper.ProcessedImages(
                wmOriginal.toString(), thumbOriginal.toString(), previewOriginal.toString())
        processingHelper.processar(editada, editada.parent, foto.id, QualidadePreview.ORIGINAL) >>
            new FotoProcessingHelper.ProcessedImages(
                wmEditada.toString(), thumbEditada.toString(), previewEditada.toString())
        pathResolver.relativo(Path.of(wmOriginal.toString())) >> "orig/wm_orig_1.jpg"
        pathResolver.relativo(Path.of(thumbOriginal.toString())) >> "orig/thumb_orig_1.jpg"
        pathResolver.relativo(Path.of(previewOriginal.toString())) >> "orig/preview_v5_orig_1.jpg"
        pathResolver.relativo(Path.of(wmEditada.toString())) >> "editadas/wm_editada_1.jpg"
        pathResolver.relativo(Path.of(thumbEditada.toString())) >> "editadas/thumb_editada_1.jpg"
        pathResolver.relativo(Path.of(previewEditada.toString())) >> "editadas/preview_v5_editada_1.jpg"

        when:
        def salva = service.regerarMarcaDagua(foto.id)

        then:
        salva.watermarkedPath == "orig/wm_orig_1.jpg"
        salva.thumbPath == "orig/thumb_orig_1.jpg"
        salva.previewPath == "orig/preview_v5_orig_1.jpg"
        salva.editadaWatermarkedPath == "editadas/wm_editada_1.jpg"
        salva.editadaThumbPath == "editadas/thumb_editada_1.jpg"
        salva.editadaPreviewPath == "editadas/preview_v5_editada_1.jpg"
        1 * previewService.removerLegados(foto)
        1 * fotoRepository.save(foto) >> foto

        cleanup:
        tmp.toFile().deleteDir()
    }

    def "rejeita regeneração quando o arquivo original não existe"() {
        given:
        def foto = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .fileName("sumida.jpg")
            .originalPath("orig/sumida.jpg")
            .watermarkedPath("orig/wm.jpg")
            .thumbPath("orig/thumb.jpg")
            .status(StatusFoto.PUBLICADA)
            .visivel(true)
            .build()
        fotoRepository.findById(foto.id) >> Optional.of(foto)
        pathResolver.resolver(foto.originalPath) >> null

        when:
        service.regerarMarcaDagua(foto.id)

        then:
        thrown(ArquivoDeImagemInvalidoException)
        0 * processingHelper.processar(_, _, _, _)
        0 * fotoRepository.save(_)
    }

    @Unroll
    def "reenvia a edição de foto já entregue com status #status"() {
        given:
        def tmp = Files.createTempDirectory("foto-reedicao-spec")
        def editada = tmp.resolve("nova-editada.png")
        def bytes = novoArquivoPng("nova-editada.png").bytes
        Files.write(editada, bytes)
        def watermarked = tmp.resolve("wm_editada.jpg")
        def thumb = tmp.resolve("thumb_editada.jpg")
        def preview = tmp.resolve("preview_editada.jpg")
        Files.write(watermarked, bytes)
        Files.write(thumb, bytes)
        Files.write(preview, bytes)
        def foto = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .fileName("IMG_1.png")
            .originalPath("orig/1.png")
            .watermarkedPath("wm/1.png")
            .thumbPath("thumb/1.png")
            .editadaPath("editadas/antiga.jpg")
            .editadaEm(LocalDateTime.now().minusDays(1))
            .dataDownload(LocalDateTime.now().minusHours(2))
            .status(status)
            .compraExtraId(compraExtraId)
            .versaoAtual(VersaoFoto.EDITADA)
            .visivel(true)
            .build()
        agendamentoReadService.isStatusPermitidoParaUpload(agendamentoId) >> true
        fotoRepository.findById(foto.id) >> Optional.of(foto)
        fileStorageService.salvarEmSubdiretorio(_, agendamentoId, "editadas") >> editada.toString()
        pathResolver.resolver(editada.toString()) >> editada
        processingHelper.processar(editada, editada.parent, foto.id, QualidadePreview.ORIGINAL) >>
            new FotoProcessingHelper.ProcessedImages(watermarked.toString(), thumb.toString(), preview.toString())
        pathResolver.relativo(Path.of(watermarked.toString())) >> "editadas/wm.jpg"
        pathResolver.relativo(Path.of(thumb.toString())) >> "editadas/thumb.jpg"
        pathResolver.relativo(Path.of(preview.toString())) >> "editadas/preview.jpg"
        fileValidator.sanitizeFilename("nova-editada.png") >> "nova-editada.png"
        fotoRepository.save(_ as FotoEnsaio) >> { args -> args[0] }

        when:
        def salva = service.reeditarFotoPublicada(
            agendamentoId, foto.id, novoArquivoPng("nova-editada.png"))

        then:
        salva.editadaPath == editada.toString()
        salva.versaoAtual == VersaoFoto.EDITADA
        salva.dataDownload != null
        1 * eventPublisher.publishEvent(_ as FotoReeditadaEvent)

        cleanup:
        tmp.toFile().deleteDir()

        where:
        status               | compraExtraId
        StatusFoto.PUBLICADA | null
        StatusFoto.PAGA      | UUID.randomUUID()
    }

    def "não reedita foto que ainda não foi entregue em versão editada"() {
        given:
        def foto = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .fileName("IMG_1.png")
            .originalPath("orig/1.png")
            .watermarkedPath("wm/1.png")
            .thumbPath("thumb/1.png")
            .status(StatusFoto.PUBLICADA)
            .versaoAtual(VersaoFoto.ORIGINAL)
            .visivel(true)
            .build()
        agendamentoReadService.isStatusPermitidoParaUpload(agendamentoId) >> true
        fotoRepository.findById(foto.id) >> Optional.of(foto)

        when:
        service.reeditarFotoPublicada(agendamentoId, foto.id, novoArquivoPng("nova.png"))

        then:
        thrown(FotoNaoEditavelException)
        0 * fileStorageService.salvarEmSubdiretorio(_, _, _)
        0 * eventPublisher.publishEvent(_)
    }

    def "não reedita foto reservada para uma seleção ativa"() {
        given:
        def foto = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .fileName("IMG_1.png")
            .originalPath("orig/1.png")
            .watermarkedPath("wm/1.png")
            .thumbPath("thumb/1.png")
            .editadaPath("editadas/1.png")
            .status(StatusFoto.PUBLICADA)
            .versaoAtual(VersaoFoto.EDITADA)
            .emSelecaoEdicao(true)
            .visivel(true)
            .build()
        agendamentoReadService.isStatusPermitidoParaUpload(agendamentoId) >> true
        fotoRepository.findById(foto.id) >> Optional.of(foto)

        when:
        service.reeditarFotoPublicada(agendamentoId, foto.id, novoArquivoPng("nova.png"))

        then:
        thrown(FotoNaoEditavelException)
        0 * fileStorageService.salvarEmSubdiretorio(_, _, _)
    }

    def "não reedita foto com arquivos expurgados"() {
        given:
        def foto = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .fileName("IMG_1.png")
            .originalPath("orig/1.png")
            .watermarkedPath("wm/1.png")
            .thumbPath("thumb/1.png")
            .editadaPath("editadas/1.png")
            .status(StatusFoto.PUBLICADA)
            .versaoAtual(VersaoFoto.EDITADA)
            .arquivosRemovidos(true)
            .visivel(true)
            .build()
        agendamentoReadService.isStatusPermitidoParaUpload(agendamentoId) >> true
        fotoRepository.findById(foto.id) >> Optional.of(foto)

        when:
        service.reeditarFotoPublicada(agendamentoId, foto.id, novoArquivoPng("nova.png"))

        then:
        thrown(ArquivoExpurgadoException)
        0 * fileStorageService.salvarEmSubdiretorio(_, _, _)
    }

    private FotoEnsaio fotoComArquivos(Path dir,
                                       StatusFoto status = StatusFoto.PUBLICADA,
                                       boolean selecionadaPacote = false,
                                       UUID compraExtraId = null) {
        def paths = (1..3).collect { indice ->
            def caminho = dir.resolve("foto-${UUID.randomUUID()}-${indice}.jpg")
            Files.write(caminho, "x".bytes)
            caminho
        }
        return FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .originalPath(paths[0].toString())
            .watermarkedPath(paths[1].toString())
            .thumbPath(paths[2].toString())
            .fileName("foto.jpg")
            .status(status)
            .selecionadaPacote(selecionadaPacote)
            .compraExtraId(compraExtraId)
            .visivel(true)
            .build()
    }

    private static MockMultipartFile novoArquivoPng(String nome) {
        return new MockMultipartFile("arquivos", nome, "image/png", imagemPng())
    }

    private static Map arquivosDeTeste(int quantidade) {
        def dir = Files.createTempDirectory("foto-service-spec")
        def originais = (0..<quantidade).collect { indice ->
            def caminho = dir.resolve("orig_${indice}.png")
            Files.write(caminho, imagemPng())
            return caminho
        }
        return [dir: dir, originais: originais]
    }

    private static byte[] imagemPng() {
        def imagem = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)
        def saida = new ByteArrayOutputStream()
        ImageIO.write(imagem, "png", saida)
        return saida.toByteArray()
    }
}
