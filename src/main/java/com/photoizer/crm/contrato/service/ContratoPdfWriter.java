package com.photoizer.crm.contrato.service;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Gera PDFs simples (texto) de forma nativa, sem dependências externas.
 * Saída em padrão A4 com encoding WinAnsi (cobre acentuação do português).
 * Responsável por materializar o snapshot imutável do contrato.
 */
@Component
public class ContratoPdfWriter {

    private static final int PAGINA_LARGURA = 595;
    private static final int PAGINA_ALTURA = 842;
    private static final int MARGEM = 50;
    private static final int MAX_CHARS_LINHA = 98;
    private static final int LINHAS_POR_PAGINA = 50;
    private static final int TAMANHO_FONTE = 10;
    private static final int ESPACO_LINHA = 14;
    private static final int INICIO_Y = PAGINA_ALTURA - MARGEM - 20;

    public byte[] gerar(String titulo, List<String> linhas) {
        var linhasQuebradas = quebrarLinhas(linhas);
        var paginas = paginar(linhasQuebradas);
        return montarPdf(titulo, paginas);
    }

    private List<String> quebrarLinhas(List<String> linhas) {
        var resultado = new ArrayList<String>();
        for (var linha : linhas) {
            if (linha == null || linha.isBlank()) {
                resultado.add("");
                continue;
            }
            var restante = linha;
            while (restante.length() > MAX_CHARS_LINHA) {
                var corte = restante.substring(0, MAX_CHARS_LINHA);
                var espaco = Math.max(corte.lastIndexOf(' '), corte.lastIndexOf(','));
                if (espaco > MAX_CHARS_LINHA / 2) {
                    resultado.add(corte.substring(0, espaco).trim());
                    restante = restante.substring(espaco).trim();
                } else {
                    resultado.add(corte);
                    restante = restante.substring(MAX_CHARS_LINHA).trim();
                }
            }
            resultado.add(restante);
        }
        return resultado;
    }

    private List<List<String>> paginar(List<String> linhas) {
        var paginas = new ArrayList<List<String>>();
        var atual = new ArrayList<String>();
        for (var linha : linhas) {
            if (atual.size() >= LINHAS_POR_PAGINA) {
                paginas.add(atual);
                atual = new ArrayList<>();
            }
            atual.add(linha);
        }
        if (!atual.isEmpty()) {
            paginas.add(atual);
        }
        if (paginas.isEmpty()) {
            paginas.add(List.of(""));
        }
        return paginas;
    }

    private byte[] montarPdf(String titulo, List<List<String>> paginas) {
        try (var out = new ByteArrayOutputStream()) {
            var objetos = new ArrayList<byte[]>();

            objetos.add(("<< /Type /Catalog /Pages 2 0 R >>").getBytes(StandardCharsets.ISO_8859_1));

            var kids = new StringBuilder();
            for (int i = 0; i < paginas.size(); i++) {
                if (kids.length() > 0) {
                    kids.append(' ');
                }
                kids.append((3 + i * 2)).append(" 0 R");
            }
            objetos.add(("<< /Type /Pages /Kids [").getBytes(StandardCharsets.ISO_8859_1));
            objetos.add(kids.toString().getBytes(StandardCharsets.ISO_8859_1));
            objetos.add("] /Count ".getBytes(StandardCharsets.ISO_8859_1));
            objetos.add(Integer.toString(paginas.size()).getBytes(StandardCharsets.ISO_8859_1));
            objetos.add(" >>".getBytes(StandardCharsets.ISO_8859_1));

            for (int i = 0; i < paginas.size(); i++) {
                int numPagina = 3 + i * 2;
                int numConteudo = 4 + i * 2;
                objetos.add(("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "
                    + PAGINA_LARGURA + " " + PAGINA_ALTURA + "] "
                    + "/Resources << /Font << /F1 5 0 R >> >> /Contents "
                    + numConteudo + " 0 R >>").getBytes(StandardCharsets.ISO_8859_1));
                var conteudo = montarConteudo(titulo, i, paginas.get(i));
                objetos.add(("<< /Length " + conteudo.length + " >>\nstream\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
                objetos.add(conteudo);
                objetos.add("\nendstream".getBytes(StandardCharsets.ISO_8859_1));
            }

            objetos.add(("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                + "/Encoding /WinAnsiEncoding >>").getBytes(StandardCharsets.ISO_8859_1));

            out.write("%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1));

            var offsets = new ArrayList<Long>();
            int numeroObjeto = 1;
            for (var obj : objetos) {
                offsets.add((long) out.size());
                out.write((numeroObjeto + " 0 obj\n").getBytes(StandardCharsets.ISO_8859_1));
                out.write(obj);
                out.write("\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
                numeroObjeto++;
            }

            long xrefPos = out.size();
            int totalObjetos = (numeroObjeto - 1) + 1;
            out.write(("xref\n0 " + totalObjetos + "\n").getBytes(StandardCharsets.ISO_8859_1));
            out.write("0000000000 65535 f \n".getBytes(StandardCharsets.ISO_8859_1));
            for (long offset : offsets) {
                out.write(String.format("%010d 00000 n \n", offset).getBytes(StandardCharsets.ISO_8859_1));
            }
            out.write(("trailer\n<< /Size " + totalObjetos + " /Root 1 0 R >>\n"
                + "startxref\n" + xrefPos + "\n%%EOF\n").getBytes(StandardCharsets.ISO_8859_1));

            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar PDF do contrato", e);
        }
    }

    private byte[] montarConteudo(String titulo, int indice, List<String> linhas) {
        var sb = new StringBuilder();
        sb.append("BT /F1 ").append(TAMANHO_FONTE).append(" Tf ")
            .append(MARGEM).append(' ').append(INICIO_Y).append(" Td 14 TL\n");
        sb.append('(').append(wina(negrito(titulo))).append(") Tj 0 -20 Td\n");
        if (indice > 0 || !linhas.isEmpty()) {
            for (var linha : linhas) {
                sb.append('(').append(wina(linha)).append(") Tj ")
                    .append(ESPACO_LINHA).append(" TL 0 -").append(ESPACO_LINHA).append(" Td\n");
            }
        }
        sb.append("ET");
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private String negrito(String texto) {
        if (texto == null) return "";
        return texto;
    }

    private String wina(String texto) {
        var sb = new StringBuilder();
        for (char c : texto.toCharArray()) {
            if (c > 255) {
                sb.append('?');
            } else if (c == '(' || c == ')' || c == '\\') {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}