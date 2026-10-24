package com.photoizer.crm.shared.pdf;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;

/**
 * Tokens visuais do PDF (cores, fontes e metricas).
 *
 * <p>Centraliza a identidade usada por todos os documentos gerados pelo
 * {@link PdfRenderer}. A paleta espelha a pagina publica da proposta
 * (teal/ciano + serifa de display), para que o termo assinado pareca a
 * continuacao natural do que o cliente leu antes de assinar.
 *
 * <p>A fonte de display ({@code PlayfairDisplay-Bold.ttf}, OFL) e embutida no
 * PDF para garantir o mesmo desenho em qualquer leitor. Se o recurso nao
 * estiver disponivel, o renderer cai automaticamente para Helvetica Bold,
 * sem quebrar a geracao.
 */
public final class PdfTheme {

    // ── Cores ───────────────────────────────────────────────────────────
    public static final Color INK = new Color(0x0F172A);
    public static final Color BODY = new Color(0x334155);
    public static final Color MUTED = new Color(0x64748B);
    public static final Color RULE = new Color(0xE2E8F0);
    public static final Color SURFACE = new Color(0xF8FAFC);

    public static final Color ACCENT = new Color(0x0E7490);
    public static final Color ACCENT_DEEP = new Color(0x0B6478);
    public static final Color ACCENT_SOFT = new Color(0xE0F2FE);
    public static final Color ACCENT_BRIGHT = new Color(0x06B6D4);

    public static final Color WARM_INK = new Color(0x9A3412);
    public static final Color WARM_SOFT = new Color(0xFFF7ED);

    public static final Color SUCCESS = new Color(0x047857);
    public static final Color SUCCESS_SOFT = new Color(0xECFDF5);

    public static final Color WHITE = Color.WHITE;
    public static final Color WHITE_SOFT = new Color(0xE6F7FA);

    // ── Metricas da pagina ──────────────────────────────────────────────
    public static final float MARGIN_LEFT = 48f;
    public static final float MARGIN_RIGHT = 48f;
    public static final float MARGIN_TOP = 56f;
    public static final float MARGIN_BOTTOM = 60f;

    private static final String SERIF_RESOURCE = "/fonts/PlayfairDisplay-Bold.ttf";
    private static final BaseFont SERIF = carregarSerif();

    private PdfTheme() {
    }

    /** Fonte de display (serifa) usada no hero e no wordmark da contratada. */
    public static Font serif(float size, Color color) {
        if (SERIF != null) {
            return new Font(SERIF, size, Font.NORMAL, color);
        }
        return new Font(Font.HELVETICA, size, Font.BOLD, color);
    }

    /** Fonte sans (Helvetica, embutida no PDF) com estilo e cor. */
    public static Font sans(float size, int style, Color color) {
        return new Font(Font.HELVETICA, size, style, color);
    }

    public static Font body() {
        return sans(9.5f, Font.NORMAL, BODY);
    }

    public static Font bodyStrong(Color color) {
        return sans(9.5f, Font.BOLD, color);
    }

    public static Font label() {
        return sans(8.5f, Font.NORMAL, MUTED);
    }

    public static Font eyebrow() {
        return sans(7f, Font.BOLD, MUTED);
    }

    public static Font mono(float size, Color color) {
        return new Font(Font.COURIER, size, Font.NORMAL, color);
    }

    private static BaseFont carregarSerif() {
        try (InputStream in = PdfTheme.class.getResourceAsStream(SERIF_RESOURCE)) {
            if (in == null) {
                return null;
            }
            var bytes = in.readAllBytes();
            return BaseFont.createFont("PlayfairDisplay-Bold.ttf", BaseFont.IDENTITY_H,
                BaseFont.EMBEDDED, true, bytes, null);
        } catch (IOException | com.lowagie.text.DocumentException e) {
            return null;
        }
    }
}
