package com.photoizer.crm.contrato.service;

import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.config.service.ConfiguracaoService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ContratoTemplateService {

    private final ConfiguracaoService configuracaoService;

    /**
     * Template padrao do contrato de prestacao de servicos fotograficos.
     * Utilizado pelo DataSeeder e pelo endpoint de restauracao de template.
     */
    public static final String TEMPLATE_PADRAO = """
= PRESTAÇÃO DE SERVIÇOS FOTOGRÁFICOS =

# 1. Dados do Cliente
Nome completo: {{clienteNome}}
CPF: {{clienteCPF}}
E-mail: {{clienteEmail}}
Telefone: {{clienteTelefone}}
Cidade / Estado: {{clienteCidade}} / {{clienteEstado}}

Contratada: {{contratadaNome}}, inscrita no CNPJ nº {{contratadaCnpj}}, com sede em {{contratadaCidade}}.

# 2. Informações do Ensaio
Data do ensaio: {{dataEnsaio}}
Horário do ensaio: {{horarioEnsaio}}
Local do ensaio: {{localEnsaio}}
Endereço completo: {{enderecoEnsaio}}
Profissionais do ensaio: {{fotografosEnsaio}}

# 3. Pacote Contratado
Pacote: {{pacoteNome}}
Inclui: serviço conforme pacote contratado.

# 4. Valores
Valor total do serviço: {{valorTotal}}
Valor pago como reserva ({{percentualEntrada}}%): {{valorEntrada}}
Valor restante a pagar no final do ensaio: {{valorRestante}}
O pagamento da reserva garante o bloqueio da data e horário na agenda da Contratada.
O valor restante deverá ser pago ao final da realização do ensaio fotográfico.

Dados para pagamento (PIX)
Chave PIX ({{pixTipoChave}}): {{pixChave}}

# 5. Entrega das Fotografias
As fotos do ensaio serão enviadas ao Cliente em até 2 dias após a realização do ensaio para que ele faça a seleção das imagens desejadas.
Após a seleção, a entrega final das fotografias ocorrerá em até 2 dias.
As fotos serão entregues em formato digital, em alta resolução.
Caso o Cliente opte por fotos extras além do pacote contratado, será cobrado o valor de {{precoFotoExtra}} por foto adicional.

# 6. Cancelamento
Caso o Cliente cancele o ensaio por qualquer motivo, o valor pago como reserva não será reembolsado, pois garante a reserva da data na agenda da Contratada.
Caso ocorra algum imprevisto que impeça a presença da Contratada, poderá haver a substituição por outro fotógrafo profissional de padrão equivalente.
Caso não seja possível a substituição, o valor pago será devolvido integralmente ao Cliente.
Se houver algum imprevisto relacionado à antecipação de voo, chuva ou doença, o ensaio será cancelado e haverá o reembolso completo do valor da reserva.

# 7. USO DE IMAGEM (OPCIONAL)
{{autorizaUsoImagem}}

# 8. Disposições Gerais
Este contrato passa a vigorar a partir da assinatura das partes.
Qualquer alteração neste contrato deverá ser realizada por escrito.

# 9. Assinatura Digital
Ao assinar este documento, o Cliente declara que leu e concorda com todos os termos acima descritos.
Resposta do cliente sobre uso de imagem: {{autorizaUsoImagem}}
Assinatura do contratante: {{clienteNome}}
Assinatura da Contratada: {{contratadaNome}}
""";

    public ContratoTemplateService(ConfiguracaoService configuracaoService) {
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
            String taxaDeslocamento,
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
            Map.entry("taxaDeslocamento", nuloVazio(taxaDeslocamento)),
            Map.entry("fotografosEnsaio", nuloVazio(profissionaisEnsaio))
        );
    }

    /**
     * @deprecated Use {@link ConfigKey#CONTRATO_TEMPLATE} diretamente.
     */
    @Deprecated
    public String getTemplateKey() {
        return ConfigKey.CONTRATO_TEMPLATE.getKey();
    }

    private String nuloVazio(String valor) {
        return valor != null ? valor : "";
    }

    private String esc(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
