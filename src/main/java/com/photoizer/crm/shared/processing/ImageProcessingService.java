package com.photoizer.crm.shared.processing;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

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

    /**
     * Thumbnail público do grid: maior que o thumb interno para ficar nítido em
     * telas retina sem obrigar o download da prévia em resolução original.
     */
    public static final int PUBLIC_THUMB_WIDTH = 600;
    public static final int PUBLIC_THUMB_HEIGHT = 400;
    public static final float PUBLIC_THUMB_QUALITY = 0.82f;

    /**
     * Versão do cache do thumb protegido. Ao mudar tamanho/qualidade,
     * incremente para que os arquivos antigos deixem de ser usados.
     */
    public static final String PUBLIC_THUMB_VERSION = "v2";

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
        var temporario = temporarioPara(target);
        try {
            Thumbnails.of(source.toFile())
                .size(width, height)
                .outputQuality(quality)
                .toFile(temporario.toFile());
            moverAtomicamente(temporario, target);
            return target;
        } finally {
            Files.deleteIfExists(temporario);
        }
    }

    public Path aplicarMarcaDagua(Path source, Path targetDir, String texto, float opacidade) throws IOException {
        var target = targetDir.resolve("wm_" + source.getFileName().toString());
        return aplicarMarcaDaguaEm(source, target, texto, opacidade, WATERMARK_MAX_DIM, WATERMARK_QUALITY);
    }

    public Path aplicarMarcaDaguaPreview(Path source, Path targetDir, String texto, float opacidade,
                                         QualidadePreview qualidade) throws IOException {
        var target = targetDir.resolve(qualidade.getPrefixoCache() + source.getFileName());
        return aplicarMarcaDaguaEm(source, target, texto, opacidade,
            qualidade.getMaxDim(), qualidade.getQualidade());
    }

    public Path aplicarMarcaDaguaEm(Path source, Path target, String texto, float opacidade,
                                    int maxDim, float quality) throws IOException {
        var imagem = ImageIO.read(source.toFile());
        if (imagem == null) {
            throw new IllegalArgumentException(
                "Não foi possível decodificar a imagem: " + source.getFileName());
        }

        int width = imagem.getWidth();
        int height = imagem.getHeight();
        BufferedImage canvas;

        if (maxDim > 0 && Math.max(width, height) > maxDim) {
            double scale = (double) maxDim / Math.max(width, height);
            width = (int) (width * scale);
            height = (int) (height * scale);
            canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            var g = canvas.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(imagem, 0, 0, width, height, null);
            g.dispose();
        } else {
            // Sem redimensionar: desenha direto na imagem carregada para não
            // duplicar um buffer full-res em memória. Tipos sem suporte a
            // Graphics2D (ex.: PNG com alfa) são convertidos para RGB.
            canvas = podeDesenharDiretamente(imagem) ? imagem : converterParaRgb(imagem);
        }

        var g2d = canvas.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(new Color(255, 255, 255, (int) (opacidade * 255)));
        // A fonte acompanha a largura para continuar legível em qualquer resolução.
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
        writeJpeg(canvas, target, quality);
        return target;
    }

    private boolean podeDesenharDiretamente(BufferedImage imagem) {
        return imagem.getType() == BufferedImage.TYPE_INT_RGB
            || imagem.getType() == BufferedImage.TYPE_3BYTE_BGR
            || imagem.getType() == BufferedImage.TYPE_INT_BGR;
    }

    private BufferedImage converterParaRgb(BufferedImage imagem) {
        var rgb = new BufferedImage(imagem.getWidth(), imagem.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = rgb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(imagem, 0, 0, Color.WHITE, null);
        g.dispose();
        return rgb;
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
        if (maxDim > 0 && Math.max(imagem.getWidth(), imagem.getHeight()) > maxDim) {
            builder.size(maxDim, maxDim);
        } else {
            builder.scale(1.0);
        }
        var temporario = temporarioPara(target);
        try {
            builder.outputQuality(quality).toFile(temporario.toFile());
            moverAtomicamente(temporario, target);
            return target;
        } finally {
            Files.deleteIfExists(temporario);
        }
    }

    private void writeJpeg(BufferedImage image, Path target, float quality) throws IOException {
        var temporario = temporarioPara(target);
        try {
            var writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) {
                ImageIO.write(image, "jpg", temporario.toFile());
            } else {
                var writer = writers.next();
                try {
                    var param = writer.getDefaultWriteParam();
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(quality);
                    try (var output = ImageIO.createImageOutputStream(temporario.toFile())) {
                        writer.setOutput(output);
                        writer.write(null, new IIOImage(image, null, null), param);
                    }
                } finally {
                    writer.dispose();
                }
            }
            moverAtomicamente(temporario, target);
        } finally {
            Files.deleteIfExists(temporario);
        }
    }

    private Path temporarioPara(Path target) {
        var fileName = target.getFileName().toString();
        int ponto = fileName.lastIndexOf('.');
        var base = ponto > 0 ? fileName.substring(0, ponto) : fileName;
        var extensao = ponto > 0 ? fileName.substring(ponto) : ".jpg";
        // Mantém a extensão para o Thumbnailator inferir o formato de saída.
        return target.resolveSibling(base + "." + UUID.randomUUID() + ".tmp" + extensao);
    }

    /**
     * Substitui o arquivo final de uma vez. Evita que duas requisições
     * concorrentes sirvam um JPEG parcialmente escrito.
     */
    private void moverAtomicamente(Path origem, Path destino) throws IOException {
        try {
            Files.move(origem, destino,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(origem, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
