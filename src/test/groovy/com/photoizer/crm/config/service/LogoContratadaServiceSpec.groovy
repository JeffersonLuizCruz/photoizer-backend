package com.photoizer.crm.config.service

import com.photoizer.crm.config.model.ConfigKey
import com.photoizer.crm.shared.exception.BadRequestException
import com.photoizer.crm.shared.storage.FileStorageService
import com.photoizer.crm.shared.storage.FileValidator
import com.photoizer.crm.shared.storage.StoragePathResolver
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class LogoContratadaServiceSpec extends Specification {

    @TempDir
    Path tempDir

    def configuracaoService = Mock(ConfiguracaoService)
    def fileStorageService = Mock(FileStorageService)
    def fileValidator = Mock(FileValidator)
    def pathResolver = Mock(StoragePathResolver)

    @Subject
    def service = new LogoContratadaService(
        configuracaoService,
        fileStorageService,
        fileValidator,
        pathResolver)

    def "salva a logo, persiste o caminho e apaga a arte anterior"() {
        given:
        def arquivo = Mock(MultipartFile)
        configuracaoService.getValor(ConfigKey.LOGO_CONTRATADA) >> "logos/anterior.png"

        when:
        def caminho = service.salvar(arquivo)

        then:
        1 * fileValidator.validate(arquivo, "logo")
        1 * fileStorageService.salvar(arquivo) >> "logos/nova.png"
        1 * configuracaoService.atualizar(ConfigKey.LOGO_CONTRATADA, "logos/nova.png")
        1 * fileStorageService.deletar("logos/anterior.png")
        caminho == "logos/nova.png"
    }

    def "rejeita arquivo invalido sem salvar nada"() {
        given:
        def arquivo = Mock(MultipartFile)
        fileValidator.validate(arquivo, "logo") >> { throw new IllegalArgumentException("Tipo de arquivo não permitido") }

        when:
        service.salvar(arquivo)

        then:
        thrown(BadRequestException)
        0 * fileStorageService.salvar(_)
        0 * configuracaoService.atualizar(_, _)
    }

    def "remove a logo personalizada e volta para a arte padrao"() {
        given:
        configuracaoService.getValor(ConfigKey.LOGO_CONTRATADA) >> "logos/atual.png"

        when:
        service.remover()

        then:
        1 * configuracaoService.atualizar(ConfigKey.LOGO_CONTRATADA, "")
        1 * fileStorageService.deletar("logos/atual.png")
    }

    def "serve a logo padrao quando nao ha upload configurado"() {
        given:
        configuracaoService.getValor(ConfigKey.LOGO_CONTRATADA) >> ""

        when:
        def logo = service.carregar()

        then:
        logo.contentType() == MediaType.IMAGE_PNG
        logo.filename() == "logo-padrao.png"
        logo.resource().exists()
    }

    def "serve o upload quando o arquivo configurado existe"() {
        given:
        def arquivo = tempDir.resolve("logo_abc.png")
        Files.write(arquivo, [0x89, 0x50, 0x4E, 0x47] as byte[])
        configuracaoService.getValor(ConfigKey.LOGO_CONTRATADA) >> "logo_abc.png"
        pathResolver.getUploadDir() >> tempDir
        pathResolver.resolver("logo_abc.png") >> arquivo

        when:
        def logo = service.carregar()

        then:
        logo.resource() instanceof FileSystemResource
        logo.contentType() == MediaType.IMAGE_PNG
        logo.filename() == "abc.png"
    }

    def "cai na logo padrao quando o arquivo configurado nao existe mais"() {
        given:
        def arquivo = tempDir.resolve("sumiu.png")
        configuracaoService.getValor(ConfigKey.LOGO_CONTRATADA) >> "sumiu.png"
        pathResolver.getUploadDir() >> tempDir
        pathResolver.resolver("sumiu.png") >> arquivo

        when:
        def logo = service.carregar()

        then:
        logo.filename() == "logo-padrao.png"
        logo.resource().exists()
    }
}
