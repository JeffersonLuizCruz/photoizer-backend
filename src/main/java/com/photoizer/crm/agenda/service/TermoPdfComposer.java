package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.shared.pdf.PdfBlock;
import com.photoizer.crm.shared.pdf.PdfDocument;
import com.photoizer.crm.shared.pdf.PdfMarkupParser;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Monta o {@link PdfDocument} do termo de prestacao de servicos assinado.
 *
 * <p>Combina dados estruturados do agendamento (cliente, ensaio, valores,
 * contratada) com o texto editavel do template (clausulas) em um documento
 * com capa, resumo, clausulas, assinaturas e autenticacao.
 *
 * <p>As clausulas continuam vindo do template configurado pelo admin: o
 * composer apenas remove as linhas de assinatura em texto e as substitui por
 * um bloco visual de assinatura, mantendo o restante do contrato intacto.
 */
@Component
public class TermoPdfComposer {

    private static final DateTimeFormatter FMT_ASSINATURA =
        DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final Set<String> CONECTIVOS = Set.of("de", "da", "do", "das", "dos", "e");

    public PdfDocument compor(Agendamento agendamento,
                              Map<String, String> placeholders,
                              String textoRenderizado,
                              DadosAssinatura dados) {
        var contratada = valor(placeholders, "contratadaNome");
        var marca = contratada.isBlank() ? "Estúdio de Fotografia" : contratada;
        var cnpj = valor(placeholders, "contratadaCnpj");

        var blocos = new ArrayList<PdfBlock>();
        blocos.add(cards(placeholders));
        var pix = calloutPix(placeholders);
        if (pix != null) {
            blocos.add(pix);
        }
        blocos.add(new PdfBlock.Divider());
        blocos.addAll(PdfMarkupParser.parse(limparTextoDoTemplate(textoRenderizado)));
        blocos.add(assinaturas(placeholders, dados));
        blocos.add(autenticacao(agendamento, dados));

        var rodape = cnpj.isBlank() ? marca : marca + " · CNPJ " + cnpj;

        return new PdfDocument(
            "Termo de Prestação de Serviços Fotográficos",
            contratada.isBlank() ? null : contratada,
            "Cliente " + valor(placeholders, "clienteNome") + " · " + valor(placeholders, "pacoteNome"),
            new PdfDocument.Hero(
                "Termo de prestação de serviços fotográficos",
                monograma(marca),
                marca,
                "Assinado eletronicamente",
                "Proposta nº " + idCurto(agendamento) + " · "
                    + dados.dataAssinatura().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))),
            fatos(placeholders),
            blocos,
            rodape,
            null);
    }

    private PdfBlock.CardGrid cards(Map<String, String> p) {
        return new PdfBlock.CardGrid(List.of(
            new PdfBlock.Card("Cliente", linhasCliente(p)),
            new PdfBlock.Card("Ensaio", linhasEnsaio(p)),
            new PdfBlock.Card("Pacote", linhasPacote(p)),
            new PdfBlock.Card("Valores", linhasValores(p))));
    }

    private List<PdfBlock.Entry> linhasCliente(Map<String, String> p) {
        var linhas = new ArrayList<PdfBlock.Entry>();
        adicionar(linhas, "Nome", valor(p, "clienteNome"));
        adicionar(linhas, "CPF", valor(p, "clienteCPF"));

        var contato = juntar(valor(p, "clienteTelefone"), valor(p, "clienteEmail"), " · ");
        adicionar(linhas, "Contato", contato);

        var cidade = valor(p, "clienteCidade");
        var estado = valor(p, "clienteEstado");
        adicionar(linhas, "Cidade / UF", juntar(cidade, estado, " / "));
        return linhas;
    }

    private List<PdfBlock.Entry> linhasEnsaio(Map<String, String> p) {
        var linhas = new ArrayList<PdfBlock.Entry>();
        adicionar(linhas, "Data", valor(p, "dataEnsaio"));
        adicionar(linhas, "Horário", juntar(valor(p, "horarioEnsaio"), valor(p, "duracaoEnsaio"), " · "));
        adicionar(linhas, "Local", valor(p, "localEnsaio"));
        adicionar(linhas, "Responsável", valor(p, "fotografoResponsavel"));
        var profissionais = nomesProfissionais(valor(p, "profissionaisEnsaio"));
        adicionar(linhas, "Profissionais", profissionais);
        return linhas;
    }

    private List<PdfBlock.Entry> linhasPacote(Map<String, String> p) {
        var linhas = new ArrayList<PdfBlock.Entry>();
        adicionar(linhas, "Pacote", valor(p, "pacoteNome"));
        adicionar(linhas, "Foto extra", valor(p, "precoFotoExtra"));
        return linhas;
    }

    private List<PdfBlock.Entry> linhasValores(Map<String, String> p) {
        var linhas = new ArrayList<PdfBlock.Entry>();
        adicionar(linhas, "Total", valor(p, "valorTotal"));

        var deslocamento = valor(p, "taxaDeslocamento");
        if (!deslocamento.isBlank() && !"R$ 0,00".equals(deslocamento)) {
            adicionar(linhas, "Deslocamento", deslocamento);
        }

        var reservaRecebida = "recebida".equalsIgnoreCase(valor(p, "statusReserva"));
        var percentual = valor(p, "percentualEntrada");
        var rotuloReserva = reservaRecebida
            ? "Reserva já recebida"
            : "Reserva" + (percentual.isBlank() ? "" : " (" + percentual + "%)");
        var entrada = valor(p, "valorEntrada");
        if (!entrada.isBlank()) {
            linhas.add(new PdfBlock.Entry(rotuloReserva, entrada, true));
        }

        adicionar(linhas, "Restante no final", valor(p, "valorRestante"));
        return linhas;
    }

    private PdfBlock.Callout calloutPix(Map<String, String> p) {
        var chave = valor(p, "pixChave");
        if (chave.isBlank()) {
            return null;
        }
        var tipo = valor(p, "pixTipoChave");
        var valor = tipo.isBlank() ? chave : tipo + " · " + chave;
        return new PdfBlock.Callout("Dados para pagamento via PIX", valor, null);
    }

    private PdfBlock.SignatureBlock assinaturas(Map<String, String> p, DadosAssinatura dados) {
        var contratante = dados.assinante() != null && !dados.assinante().isBlank()
            ? dados.assinante()
            : valor(p, "clienteNome");

        var cpf = valor(p, "clienteCPF");
        var cnpj = valor(p, "contratadaCnpj");

        return new PdfBlock.SignatureBlock(List.of(
            new PdfBlock.Signature("Contratante", contratante, cpf.isBlank() ? null : "CPF " + cpf),
            new PdfBlock.Signature("Contratada",
                valor(p, "contratadaNome"),
                cnpj.isBlank() ? null : "CNPJ " + cnpj)));
    }

    private PdfBlock.MetaBox autenticacao(Agendamento agendamento, DadosAssinatura dados) {
        var linhas = new ArrayList<PdfBlock.Entry>();
        linhas.add(new PdfBlock.Entry("Proposta",
            agendamento.getId() != null ? agendamento.getId().toString() : "—"));
        linhas.add(new PdfBlock.Entry("Assinado em", dados.dataAssinatura().format(FMT_ASSINATURA)));
        linhas.add(new PdfBlock.Entry("Endereço IP",
            dados.ip() == null || dados.ip().isBlank() ? "não informado" : dados.ip()));
        linhas.add(new PdfBlock.Entry("Código SHA-256", agruparHash(dados.hash())));
        return new PdfBlock.MetaBox("Autenticação do documento", linhas);
    }

    private List<PdfDocument.Fact> fatos(Map<String, String> p) {
        return List.of(
            new PdfDocument.Fact("Data do ensaio", ouTraco(valor(p, "dataEnsaio"))),
            new PdfDocument.Fact("Local", ouTraco(valor(p, "localEnsaio"))),
            new PdfDocument.Fact("Pacote", ouTraco(valor(p, "pacoteNome"))));
    }

    /**
     * Remove as linhas de assinatura em texto do template: elas viram o bloco
     * visual de assinatura, evitando duplicidade no documento final.
     */
    static String limparTextoDoTemplate(String texto) {
        if (texto == null) {
            return "";
        }
        var sb = new StringBuilder();
        for (var linha : texto.split("\n", -1)) {
            var trim = linha.strip();
            if (trim.startsWith("Assinatura do contratante:")
                || trim.startsWith("Assinatura da Contratada:")
                || trim.startsWith("Aceite registrado em")
                || trim.startsWith("Hash do documento:")) {
                continue;
            }
            sb.append(linha).append('\n');
        }
        return sb.toString();
    }

    /**
     * Iniciais da contratada para o selo do cabecalho (ex.: "Carol Oliva
     * Fotografia" -> "CO"). Ignora conectivos para nao gerar "CD".
     */
    static String monograma(String nome) {
        if (nome == null || nome.isBlank()) {
            return "";
        }
        var iniciais = new StringBuilder();
        for (var palavra : nome.strip().split("\\s+")) {
            if (palavra.isBlank() || CONECTIVOS.contains(palavra.toLowerCase(Locale.ROOT))) {
                continue;
            }
            iniciais.append(Character.toUpperCase(palavra.charAt(0)));
            if (iniciais.length() == 2) {
                break;
            }
        }
        return iniciais.toString();
    }

    private static String nomesProfissionais(String bloco) {
        if (bloco == null || bloco.isBlank()) {
            return "";
        }
        return bloco.lines()
            .map(String::strip)
            .filter(linha -> linha.startsWith("- "))
            .map(linha -> linha.substring(2).strip())
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }

    private static void adicionar(List<PdfBlock.Entry> linhas, String rotulo, String valor) {
        if (valor != null && !valor.isBlank()) {
            linhas.add(new PdfBlock.Entry(rotulo, valor));
        }
    }

    private static String juntar(String a, String b, String separador) {
        if (a == null || a.isBlank()) {
            return b == null ? "" : b;
        }
        if (b == null || b.isBlank()) {
            return a;
        }
        return a + separador + b;
    }

    private static String valor(Map<String, String> p, String chave) {
        var valor = p.get(chave);
        return valor == null ? "" : valor.strip();
    }

    private static String ouTraco(String valor) {
        return valor == null || valor.isBlank() ? "—" : valor;
    }

    private static String idCurto(Agendamento agendamento) {
        if (agendamento.getId() == null) {
            return "—";
        }
        var id = agendamento.getId().toString();
        return id.length() > 8 ? id.substring(0, 8).toUpperCase(Locale.ROOT) : id.toUpperCase(Locale.ROOT);
    }

    private static String agruparHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return "—";
        }
        var sb = new StringBuilder(hash.length() + hash.length() / 8);
        for (int i = 0; i < hash.length(); i += 8) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(hash, i, Math.min(hash.length(), i + 8));
        }
        return sb.toString();
    }

    /** Dados do aceite usados no rodape de assinatura e na autenticacao. */
    public record DadosAssinatura(String assinante, LocalDateTime dataAssinatura, String ip, String hash) {
    }
}
