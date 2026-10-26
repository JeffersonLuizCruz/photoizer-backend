package com.photoizer.crm.shared.processing;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * PATTERN: Shared Infrastructure
 * Serviço de processamento técnico de imagens (Thumbnailator + AWT).
 * Sem regra de negócio de domínio — pertence à infraestrutura compartilhada.
 * Usado pelos módulos foto, ecommerce e edicao.
 */
@Service
public class ImageProcessingService {

    public static final int THUMB_WIDTH = 300;
    public static final int THUMB_HEIGHT = 200;

    /** Watermark interno (admin/legado), mantido em 1600px. */
    public static final int WATERMARK_MAX_DIM = 1600;
    public static final float WATERMARK_QUALITY = 0.85f;

    /** Prévia pública protegida: baixa resolução para dificultar impressão. */
    public static final int PREVIEW_MAX_DIM = 800;
    public static final float PREVIEW_QUALITY = 0.60f;

    /**
     * Versão do cache da prévia pública. Ao mudar tamanho/qualidade, incremente
     * este valor para que os arquivos antigos deixem de ser usados.
     */
    public static final String PREVIEW_VERSION = "v2";

    /** Capa/hero: alta qualidade para a experiência emocional. */
    public static final int HERO_MAX_DIM = 2560;
    public static final float HERO_QUALITY = 0.90f;

    public Path gerarThumbnail(Path source, Path targetDir) throws IOException {
        var target = targetDir.resolve("thumb_" + source.getFileName().toString());
        return gerarThumbnailProtegido(source, target, THUMB_WIDTH, THUMB_HEIGHT, 0.7f);
    }

    /**
     * Gera um thumbnail a partir de uma imagem que já contém watermark.
     * Usado pelo grid público para que até a miniatura mantenha a proteção.
     */
    public Path gerarThumbnailProtegido(Path source, Path target, int width, int height, float quality)
        throws IOException {
        Thumbnails.of(source.toFile())
            .size(width, height)
            .outputQuality(quality)
            .toFile(target.toFile());
        return target;
    }

    public Path aplicarMarcaDagua(Path source, Path targetDir, String texto, float opacidade) throws IOException {
        var target = targetDir.resolve("wm_" + source.getFileName().toString());
        return aplicarMarcaDaguaEm(source, target, texto, opacidade, WATERMARK_MAX_DIM, WATERMARK_QUALITY);
    }

    public Path aplicarMarcaDaguaPreview(Path source, Path targetDir, String texto, float opacidade) throws IOException {
        var target = targetDir.resolve("preview_" + PREVIEW_VERSION + "_" + source.getFileName());
        return aplicarMarcaDaguaEm(source, target, texto, opacidade, PREVIEW_MAX_DIM, PREVIEW_QUALITY);
    }

    public Path aplicarMarcaDaguaEm(Path source, Path target, String texto, float opacidade,
                                    int maxDim, float quality) throws IOException {
        var original = ImageIO.read(source.toFile());
        if (original == null) {
            throw new IllegalArgumentException(
                "Não foi possível decodificar a imagem: " + source.getFileName());
        }

        int width = original.getWidth();
        int height = original.getHeight();

        if (Math.max(width, height) > maxDim) {
            double scale = (double) maxDim / Math.max(width, height);
            width = (int) (width * scale);
            height = (int) (height * scale);
            var scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            var g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(original, 0, 0, width, height, null);
            g.dispose();
            original = scaled;
        }

        var watermarked = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g2d = watermarked.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(original, 0, 0, null);

        g2d.setColor(new Color(255, 255, 255, (int) (opacidade * 255)));
        // A fonte acompanha a largura para continuar legível em 1024, 1600 e 2560px.
        float fontSize = Math.max(22f, width / 24f);
        var font = g2d.getFont().deriveFont(fontSize);
        g2d.setFont(font);

        var fm = g2d.getFontMetrics();
        var textWidth = fm.stringWidth(texto);
        var textHeight = fm.getHeight();
        int passoX = Math.max(1, textWidth + (int) (fontSize * 2.0f));
        int passoY = Math.max(1, textHeight + (int) (fontSize * 2.5f));

        for (int x = 0; x < width; x += passoX) {
            for (int y = textHeight; y < height; y += passoY) {
                g2d.drawString(texto, x, y);
            }
        }

        g2d.dispose();
        writeJpeg(watermarked, target, quality);
        return target;
    }

    /**
     * Reduz uma imagem que já contém marca d'água gravada (fotos antigas),
     * preservando a marca original em vez de aplicar uma nova. Imagens menores
     * que o limite são apenas recomprimidas, nunca ampliadas.
     */
    public Path redimensionarComMarcaExistente(Path source, Path target, int maxDim, float quality) throws IOException {
        var imagem = ImageIO.read(source.toFile());
        if (imagem == null) {
            throw new IllegalArgumentException(
                "Não foi possível decodificar a imagem: " + source.getFileName());
        }
        var builder = Thumbnails.of(source.toFile());
        if (Math.max(imagem.getWidth(), imagem.getHeight()) > maxDim) {
            builder.size(maxDim, maxDim);
        } else {
            builder.scale(1.0);
        }
        builder.outputQuality(quality).toFile(target.toFile());
        return target;
    }

    private void writeJpeg(BufferedImage image, Path target, float quality) throws IOException {
        var writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            ImageIO.write(image, "jpg", target.toFile());
            return;
        }
        var writer = writers.next();
        try {
            var param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            try (var output = ImageIO.createImageOutputStream(target.toFile())) {
                writer.setOutput(output);
                writer.write(null, new IIOImage(image, null, null), param);
            }
        } finally {
            writer.dispose();
        }
    }
}
