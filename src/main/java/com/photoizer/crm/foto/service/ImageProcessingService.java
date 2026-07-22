package com.photoizer.crm.foto.service;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

@Service
public class ImageProcessingService {

    public static final int THUMB_WIDTH = 300;
    public static final int THUMB_HEIGHT = 200;

    public Path gerarThumbnail(Path source, Path targetDir) throws IOException {
        var target = targetDir.resolve("thumb_" + source.getFileName().toString());
        Thumbnails.of(source.toFile())
            .size(THUMB_WIDTH, THUMB_HEIGHT)
            .outputQuality(0.7)
            .toFile(target.toFile());
        return target;
    }

    public Path aplicarMarcaDagua(Path source, Path targetDir, String texto, float opacidade) throws IOException {
        var target = targetDir.resolve("wm_" + source.getFileName().toString());

        var original = ImageIO.read(source.toFile());
        int width = original.getWidth();
        int height = original.getHeight();

        var watermarked = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g2d = watermarked.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(original, 0, 0, null);

        g2d.setColor(new Color(255, 255, 255, (int) (opacidade * 255)));
        var font = g2d.getFont().deriveFont(24f);
        g2d.setFont(font);

        var fm = g2d.getFontMetrics();
        var textWidth = fm.stringWidth(texto);
        var textHeight = fm.getHeight();

        for (int x = 0; x < width; x += textWidth + 80) {
            for (int y = textHeight; y < height; y += textHeight + 80) {
                g2d.drawString(texto, x, y);
            }
        }

        g2d.dispose();
        ImageIO.write(watermarked, "jpg", target.toFile());

        return target;
    }
}
