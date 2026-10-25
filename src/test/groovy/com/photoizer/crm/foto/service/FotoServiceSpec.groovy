package com.photoizer.crm.foto.service

import com.photoizer.crm.foto.acl.AgendamentoReadService
import com.photoizer.crm.foto.event.FotosRemovidasEvent
import com.photoizer.crm.foto.exception.ArquivoDeImagemInvalidoException
import com.photoizer.crm.foto.exception.FotoEnsaioNaoEncontradaException
import com.photoizer.crm.foto.exception.FotoNaoPertenceAoAgendamentoException
import com.photoizer.crm.foto.exception.FotoVinculadaACompraException
import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.model.StatusFoto
import com.photoizer.crm.foto.repository.FotoEnsaioRepository
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

class FotoServiceSpec extends Specification {

    def agendamentoId = UUID.randomUUID()
    def fotoRepository = Mock(FotoEnsaioRepository)
    def fileStorageService = Mock(FileStorageService)
    def processingHelper = Mock(FotoProcessingHelper)
    def agendamentoReadService = Mock(AgendamentoReadService)
    def fileValidator = Mock(FileValidator)
    def pathResolver = Mock(StoragePathResolver)
    def eventPublisher = Mock(ApplicationEventPublisher)

    @Subject
    def service = new FotoService(
        fotoRepository,
        fileStorageService,
        processingHelper,
        agendamentoReadService,
        fileValidator,
        pathResolver,
        eventPublisher
    )

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

    def "remove arquivos gravados quando o processamento falha"() {
        given:
        def tmp = arquivosDeTeste(1)
        def original = tmp.originais[0]
        agendamentoReadService.isStatusPermitidoParaUpload(agendamentoId) >> true
        fotoRepository.findMaxOrdemByAgendamentoId(agendamentoId) >> -1
        fileStorageService.salvarEmSubdiretorio(_, agendamentoId, "orig") >> original.toString()
        pathResolver.resolver(original.toString()) >> original
        processingHelper.processar(original, original.parent, _ as UUID) >> {
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
        processingHelper.processar(original, original.parent, _ as UUID) >> {
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

        agendamentoReadService.isStatusPermitidoParaUpload(agendamentoId) >> true
        fotoRepository.findMaxOrdemByAgendamentoId(agendamentoId) >> 4
        fileStorageService.salvarEmSubdiretorio(_, agendamentoId, "orig") >>> [
            primeira.toString(),
            segunda.toString()
        ]
        pathResolver.resolver(_ as String) >> { String caminho ->
            tmp.originais.find { it.toString() == caminho }
        }
        processingHelper.processar(primeira, tmp.dir, _ as UUID) >>
            new FotoProcessingHelper.ProcessedImages(watermark.toString(), thumb.toString())
        processingHelper.processar(segunda, tmp.dir, _ as UUID) >>
            new FotoProcessingHelper.ProcessedImages(watermark.toString(), thumb.toString())
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
