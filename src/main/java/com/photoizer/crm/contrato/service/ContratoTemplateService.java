package com.photoizer.crm.contrato.service;

import com.photoizer.crm.config.service.ConfiguracaoService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ContratoTemplateService {

    private static final String TEMPLATE_KEY = "contratoTemplateTexto";

    private final ConfiguracaoService configuracaoService;

    public ContratoTemplateService(ConfiguracaoService configuracaoService) {
        this.configuracaoService = configuracaoService;
    }

    public String carregarTemplate() {
        return configuracaoService.getValorTexto(TEMPLATE_KEY, null);
    }

    public String renderizarTexto(String template, Map<String, String> valores) {
        if (template == null) return "";
        var resultado = template;
        for (var entry : valores.entrySet()) {
            var chave = "{{" + entry.getKey() + "}}";
            var valor = entry.getValue() != null ? entry.getValue() : "";
            resultado = resultado.replace(chave, valor);
        }
        return resultado;
    }

    public String renderizarHtmlPublico(String template, Map<String, String> valores) {
        var textoSemSecoesCliente = removerSecoesCliente(template);
        return renderizarHtml(textoSemSecoesCliente, valores);
    }

    private String removerSecoesCliente(String template) {
        var linhas = template.split("\n", -1);
        var resultado = new StringBuilder();
        var ignorar = false;
        for (var linha : linhas) {
            var trim = linha.trim();
            if (trim.startsWith("# ")) {
                ignorar = trim.contains("1. Dados do Cliente")
                    || trim.contains("7. USO DE IMAGEM")
                    || trim.contains("9. Assinatura Digital");
                if (!ignorar) {
                    resultado.append(linha).append("\n");
                }
            } else if (!ignorar) {
                resultado.append(linha).append("\n");
            }
        }
        return resultado.toString();
    }

    public String renderizarHtml(String template, Map<String, String> valores) {
        var texto = renderizarTexto(template, valores);
        var linhas = texto.split("\n", -1);
        var html = new StringBuilder();
        for (var linha : linhas) {
            var trim = linha.trim();
            if (trim.startsWith("= ") && trim.endsWith(" =")) {
                html.append("<h1 class=\"text-center text-xl font-bold my-6\">")
                    .append(esc(trim.substring(2, trim.length() - 2).trim()))
                    .append("</h1>\n");
            } else if (trim.startsWith("# ")) {
                html.append("<h2 class=\"text-base font-semibold mt-6 mb-2\">")
                    .append(esc(trim.substring(2).trim()))
                    .append("</h2>\n");
            } else if (trim.startsWith("- ")) {
                html.append("<li class=\"ml-4 text-sm\">")
                    .append(esc(trim.substring(2).trim()))
                    .append("</li>\n");
            } else if (trim.isEmpty()) {
                html.append("<div class=\"h-3\"></div>\n");
            } else {
                html.append("<p class=\"text-sm mb-1\">")
                    .append(esc(linha))
                    .append("</p>\n");
            }
        }
        return html.toString();
    }

    public Map<String, String> buildPlaceholders(
            String clienteNome, String clienteCpf, String clienteTelefone, String clienteEmail,
            String clienteCidade, String clienteEstado,
            String dataEnsaio, String horarioEnsaio, String localEnsaio, String enderecoEnsaio,
            String pacoteNome, String precoFotoExtra,
            String valorTotal, String valorEntrada, String percentualEntrada, String valorRestante,
            String contratadaNome, String contratadaCnpj, String contratadaCidade,
            String pixChave, String pixTipoChave,
            String autorizaUsoImagem,
            String taxaDeslocamento) {
        return Map.ofEntries(
            Map.entry("clienteNome", nuloVazio(clienteNome)),
            Map.entry("clienteCPF", nuloVazio(clienteCpf)),
            Map.entry("clienteTelefone", nuloVazio(clienteTelefone)),
            Map.entry("clienteEmail", nuloVazio(clienteEmail)),
            Map.entry("clienteCidade", nuloVazio(clienteCidade)),
            Map.entry("clienteEstado", nuloVazio(clienteEstado)),
            Map.entry("dataEnsaio", nuloVazio(dataEnsaio)),
            Map.entry("horarioEnsaio", nuloVazio(horarioEnsaio)),
            Map.entry("localEnsaio", nuloVazio(localEnsaio)),
            Map.entry("enderecoEnsaio", nuloVazio(enderecoEnsaio)),
            Map.entry("pacoteNome", nuloVazio(pacoteNome)),
            Map.entry("precoFotoExtra", nuloVazio(precoFotoExtra)),
            Map.entry("valorTotal", nuloVazio(valorTotal)),
            Map.entry("valorEntrada", nuloVazio(valorEntrada)),
            Map.entry("percentualEntrada", nuloVazio(percentualEntrada)),
            Map.entry("valorRestante", nuloVazio(valorRestante)),
            Map.entry("contratadaNome", nuloVazio(contratadaNome)),
            Map.entry("contratadaCnpj", nuloVazio(contratadaCnpj)),
            Map.entry("contratadaCidade", nuloVazio(contratadaCidade)),
            Map.entry("pixChave", nuloVazio(pixChave)),
            Map.entry("pixTipoChave", nuloVazio(pixTipoChave)),
            Map.entry("autorizaUsoImagem", nuloVazio(autorizaUsoImagem)),
            Map.entry("taxaDeslocamento", nuloVazio(taxaDeslocamento))
        );
    }

    public String getTemplateKey() {
        return TEMPLATE_KEY;
    }

    private String nuloVazio(String valor) {
        return valor != null ? valor : "";
    }

    private String esc(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}