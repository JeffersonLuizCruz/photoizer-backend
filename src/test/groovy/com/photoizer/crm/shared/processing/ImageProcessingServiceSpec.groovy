package com.photoizer.crm.shared.processing

import spock.lang.Specification
import spock.lang.Subject

import javax.imageio.ImageIO
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path

class ImageProcessingServiceSpec extends Specification {

    @Subject
    def service = new ImageProcessingService()

    Path dir

    def setup() {
        dir = Files.createTempDirectory("image-processing-spec")
    }

    def cleanup() {
        dir?.toFile()?.deleteDir()
    }

    def "prévia sem limite mantém a resolução original"() {
        given:
        def original = imagem("original.jpg", 1600, 900, Color.BLACK)
        def alvo = dir.resolve("preview.jpg")

        when:
        service.aplicarMarcaDaguaEm(original, alvo, "© Estúdio", 0.35f,
            ImageProcessingService.PREVIEW_MAX_DIM, ImageProcessingService.PREVIEW_QUALITY)

        then:
        def gerada = ImageIO.read(alvo.toFile())
        gerada.width == 1600
        gerada.height == 900
        !arquivosTemporarios()
        // A marca branca sobre o fundo preto precisa aparecer na imagem final.
        pixelsClaros(gerada) > 0
    }

    def "prévia com limite reduz somente o lado maior"() {
        given:
        def original = imagem("original.jpg", 1600, 900, Color.BLACK)
        def alvo = dir.resolve("preview.jpg")

        when:
        service.aplicarMarcaDaguaEm(original, alvo, "© Estúdio", 0.35f, 400, 0.9f)

        then:
        def gerada = ImageIO.read(alvo.toFile())
        Math.max(gerada.width, gerada.height) == 400
        gerada.height == 225
    }

    def "thumbnail protegido respeita o tamanho público e não deixa temporários"() {
        given:
        def original = imagem("original.jpg", 1600, 900, Color.BLACK)
        def alvo = dir.resolve("thumb.jpg")

        when:
        service.gerarThumbnailProtegido(original, alvo,
            ImageProcessingService.PUBLIC_THUMB_WIDTH,
            ImageProcessingService.PUBLIC_THUMB_HEIGHT,
            ImageProcessingService.PUBLIC_THUMB_QUALITY)

        then:
        def gerada = ImageIO.read(alvo.toFile())
        gerada.width == 600
        Math.abs(gerada.height - 338) <= 1
        !arquivosTemporarios()
    }

    private Path imagem(String nome, int largura, int altura, Color cor) {
        def imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB)
        def g = imagem.createGraphics()
        g.setColor(cor)
        g.fillRect(0, 0, largura, altura)
        g.dispose()
        def caminho = dir.resolve(nome)
        ImageIO.write(imagem, "jpg", caminho.toFile())
        return caminho
    }

    private boolean arquivosTemporarios() {
        Files.list(dir).withCloseable { arquivos ->
            arquivos.anyMatch { it.fileName.toString().contains(".tmp.") }
        }
    }

    private int pixelsClaros(BufferedImage imagem) {
        int claros = 0
        for (int y = 0; y < imagem.height; y++) {
            for (int x = 0; x < imagem.width; x += 2) {
                def rgb = new Color(imagem.getRGB(x, y))
                if (rgb.red > 40 && rgb.green > 40 && rgb.blue > 40) {
                    claros++
                }
            }
        }
        return claros
    }
}
