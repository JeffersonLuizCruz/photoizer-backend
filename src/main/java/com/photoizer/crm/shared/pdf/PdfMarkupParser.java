package com.photoizer.crm.shared.pdf;

import java.util.ArrayList;
import java.util.List;

/**
 * Converte o texto do template do contrato (marcacao simples, editavel pelo
 * admin) em {@link PdfBlock}s.
 *
 * <p>Marcacao suportada:
 * <ul>
 *   <li>{@code = TITULO =} — titulo do documento (o primeiro e consumido pelo hero);</li>
 *   <li>{@code # 1. Secao} — titulo de secao numerado;</li>
 *   <li>{@code - item} — item de lista;</li>
 *   <li>{@code Rotulo: valor} — par rotulo/valor alinhado;</li>
 *   <li>linha curta sem pontuacao — rotulo em destaque (ex.: "Dados para pagamento (PIX)");</li>
 *   <li>demais linhas — paragrafo de corpo.</li>
 * </ul>
 *
 * <p>A conversao e tolerante: qualquer construcao nao reconhecida vira
 * paragrafo, portanto um template editado nunca impede a geracao do PDF.
 */
public final class PdfMarkupParser {

    private static final int LABEL_MAX_LENGTH = 48;
    private static final float BLANK_LINE_SPACING = 3f;

    private PdfMarkupParser() {
    }

    public static List<PdfBlock> parse(String texto) {
        var blocos = new ArrayList<PdfBlock>();
        if (texto == null || texto.isBlank()) {
            return blocos;
        }

        var pares = new ArrayList<PdfBlock.Entry>();
        var itens = new ArrayList<String>();
        var primeiraLinhaConteudo = true;
        var linhaEmBranco = false;

        for (var bruta : texto.split("\n", -1)) {
            var linha = bruta.strip();

            if (linha.isEmpty()) {
                fecharPares(pares, blocos);
                fecharItens(itens, blocos);
                linhaEmBranco = !blocos.isEmpty();
                continue;
            }

            if (linhaEmBranco && !(linha.startsWith("- "))) {
                blocos.add(new PdfBlock.Spacer(BLANK_LINE_SPACING));
            }
            linhaEmBranco = false;

            if (ehTitulo(linha)) {
                fecharPares(pares, blocos);
                fecharItens(itens, blocos);
                var titulo = linha.substring(2, linha.length() - 2).strip();
                if (primeiraLinhaConteudo) {
                    primeiraLinhaConteudo = false;
                    continue;
                }
                if (!titulo.isEmpty()) {
                    blocos.add(new PdfBlock.SectionTitle(null, titulo));
                }
                continue;
            }

            primeiraLinhaConteudo = false;

            if (linha.startsWith("# ")) {
                fecharPares(pares, blocos);
                fecharItens(itens, blocos);
                blocos.add(tituloDeSecao(linha.substring(2).strip()));
                continue;
            }

            if (linha.startsWith("- ")) {
                fecharPares(pares, blocos);
                var item = linha.substring(2).strip();
                if (!item.isEmpty()) {
                    itens.add(item);
                }
                continue;
            }

            fecharItens(itens, blocos);

            var par = parRotuloValor(linha);
            if (par != null) {
                pares.add(par);
                continue;
            }

            fecharPares(pares, blocos);

            if (ehRotuloDestacado(linha)) {
                blocos.add(new PdfBlock.Subheading(linha.endsWith(":") ? linha.substring(0, linha.length() - 1) : linha));
            } else {
                blocos.add(new PdfBlock.Paragraph(linha));
            }
        }

        fecharPares(pares, blocos);
        fecharItens(itens, blocos);
        return blocos;
    }

    private static void fecharPares(List<PdfBlock.Entry> pares, List<PdfBlock> blocos) {
        if (pares.isEmpty()) {
            return;
        }
        blocos.add(new PdfBlock.KeyValues(List.copyOf(pares)));
        pares.clear();
    }

    private static void fecharItens(List<String> itens, List<PdfBlock> blocos) {
        if (itens.isEmpty()) {
            return;
        }
        blocos.add(new PdfBlock.Bullets(List.copyOf(itens)));
        itens.clear();
    }

    private static boolean ehTitulo(String linha) {
        return linha.length() > 4 && linha.startsWith("= ") && linha.endsWith(" =");
    }

    private static PdfBlock.SectionTitle tituloDeSecao(String texto) {
        var matcher = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)*)[.)]?\\s+(.*)$").matcher(texto);
        if (matcher.matches()) {
            return new PdfBlock.SectionTitle(matcher.group(1), matcher.group(2).strip());
        }
        return new PdfBlock.SectionTitle(null, texto);
    }

    /**
     * Reconhece "Rotulo: valor" quando o rotulo e curto, tem letras e nao
     * parece uma frase inteira. Retorna {@code null} quando a linha deve ser
     * tratada como paragrafo/rotulo.
     */
    static PdfBlock.Entry parRotuloValor(String linha) {
        var idx = linha.indexOf(':');
        if (idx <= 0) {
            return null;
        }
        var rotulo = linha.substring(0, idx).strip();
        var valor = linha.substring(idx + 1).strip();
        if (rotulo.isEmpty() || rotulo.length() > LABEL_MAX_LENGTH || valor.isEmpty()) {
            return null;
        }
        if (!rotulo.matches(".*[\\p{L}].*")) {
            return null;
        }
        if (rotulo.matches("^\\d+$")) {
            return null;
        }
        if (rotulo.equalsIgnoreCase("http") || rotulo.equalsIgnoreCase("https")
            || rotulo.equalsIgnoreCase("ftp") || rotulo.equalsIgnoreCase("mailto")) {
            return null;
        }
        return new PdfBlock.Entry(rotulo, valor);
    }

    /**
     * Linha curta, sem pontuacao final e sem parenteses de checkbox vira um
     * rotulo em destaque (ex.: "Dados para pagamento (PIX)", "Inclui:").
     */
    static boolean ehRotuloDestacado(String linha) {
        if (linha.startsWith("(")) {
            return false;
        }
        if (linha.endsWith(":")) {
            return true;
        }
        if (linha.length() > LABEL_MAX_LENGTH) {
            return false;
        }
        var ultimo = linha.charAt(linha.length() - 1);
        return ultimo != '.' && ultimo != '!' && ultimo != '?';
    }
}
