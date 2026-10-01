package com.photoizer.crm.agenda.service;

import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.config.service.ConfiguracaoService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Renderização do template do termo de prestação de serviços usado na
 * página pública de assinatura da proposta. O template é armazenado no
 * módulo config (chave texto) e materializado com placeholders {{...}}.
 */
@Service
public class PropostaTemplateService {

    private final ConfiguracaoService configuracaoService;

    public PropostaTemplateService(ConfiguracaoService configuracaoService) {
        this.configuracaoService = configuracaoService;
    }

    public String carregarTemplate() {
        return configuracaoService.getValor(ConfigKey.CONTRATO_TEMPLATE);
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
        return renderizarHtml(removerSecoesCliente(template), valores);
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
        var dentroDeLista = false;
        for (var linha : linhas) {
            var trim = linha.trim();

            if (trim.startsWith("- ")) {
                if (!dentroDeLista) {
                    html.append("<ul>\n");
                    dentroDeLista = true;
                }
                html.append("<li>").append(esc(trim.substring(2).trim())).append("</li>\n");
                continue;
            }

            if (dentroDeLista) {
                html.append("</ul>\n");
                dentroDeLista = false;
            }

            if (trim.startsWith("= ") && trim.endsWith(" =")) {
                html.append("<h1>")
                    .append(esc(trim.substring(2, trim.length() - 2).trim()))
                    .append("</h1>\n");
            } else if (trim.startsWith("# ")) {
                html.append("<h2>")
                    .append(esc(trim.substring(2).trim()))
                    .append("</h2>\n");
            } else if (trim.isEmpty()) {
                // espaçamento tratado via CSS no frontend
            } else {
                html.append("<p>")
                    .append(esc(linha))
                    .append("</p>\n");
            }
        }

        if (dentroDeLista) {
            html.append("</ul>\n");
        }

        return html.toString();
    }

    public Map<String, String> buildPlaceholders(
            String clienteNome, String clienteCpf, String clienteTelefone, String clienteEmail,
            String clienteCidade, String clienteEstado,
            String dataEnsaio, String horarioEnsaio, String localEnsaio,
            String pacoteNome, String precoFotoExtra,
            String valorTotal, String valorEntrada, String percentualEntrada, String valorRestante,
            String contratadaNome, String contratadaCnpj, String contratadaCidade,
            String pixChave, String pixTipoChave,
            String autorizaUsoImagem,
            String taxaDeslocamento,
            String fotografoResponsavel,
            String profissionaisEnsaio) {
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
            Map.entry("taxaDeslocamento", nuloVazio(taxaDeslocamento)),
            Map.entry("fotografoResponsavel", nuloVazio(fotografoResponsavel)),
            Map.entry("profissionaisEnsaio", nuloVazio(profissionaisEnsaio))
        );
    }

    private String nuloVazio(String valor) {
        return valor != null ? valor : "";
    }

    private String esc(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
