package com.photoizer.crm.contrato.service;

import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.contrato.api.ContratoPublicoResponse;
import com.photoizer.crm.contrato.api.ContratoStatusPublicoResponse;
import com.photoizer.crm.contrato.event.ContratoAssinadoEvent;
import com.photoizer.crm.contrato.exception.ContratoEstadoInvalidoException;
import com.photoizer.crm.contrato.exception.ContratoNaoEncontradoException;
import com.photoizer.crm.contrato.exception.ContratoTokenExpiradoException;
import com.photoizer.crm.contrato.model.Assinatura;
import com.photoizer.crm.contrato.model.Contrato;
import com.photoizer.crm.contrato.model.StatusContrato;
import com.photoizer.crm.contrato.repository.AssinaturaRepository;
import com.photoizer.crm.contrato.repository.ContratoRepository;
import com.photoizer.crm.shared.storage.FileStorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ContratoPublicoService {

    private static final DateTimeFormatter FMT_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ContratoRepository contratoRepository;
    private final AssinaturaRepository assinaturaRepository;
    private final FileStorageService fileStorageService;
    private final ContratoPdfWriter pdfWriter;
    private final ConfiguracaoService configuracaoService;
    private final ApplicationEventPublisher eventPublisher;

    public ContratoPublicoService(ContratoRepository contratoRepository,
                                  AssinaturaRepository assinaturaRepository,
                                  FileStorageService fileStorageService,
                                  ContratoPdfWriter pdfWriter,
                                  ConfiguracaoService configuracaoService,
                                  ApplicationEventPublisher eventPublisher) {
        this.contratoRepository = contratoRepository;
        this.assinaturaRepository = assinaturaRepository;
        this.fileStorageService = fileStorageService;
        this.pdfWriter = pdfWriter;
        this.configuracaoService = configuracaoService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public ContratoPublicoResponse buscarPublico(String token) {
        var contrato = buscarPorToken(token);
        validarExpiracao(contrato);

        return ContratoPublicoResponse.of(
            contrato,
            configuracaoService.getValorTexto("nomeContratada", "Carol Oliva Fotografia"),
            configuracaoService.getValorTexto("cnpjContratada", ""),
            configuracaoService.getValorTexto("enderecoContratada", ""),
            configuracaoService.getValorTexto("pixChave", ""),
            configuracaoService.getValorTexto("pixTipoChave", "CNPJ")
        );
    }

    @Transactional(readOnly = true)
    public ContratoStatusPublicoResponse status(String token) {
        var contrato = buscarPorToken(token);
        validarExpiracao(contrato);
        return ContratoStatusPublicoResponse.of(contrato);
    }

    public Contrato assinar(String token,
                            String nome,
                            String telefone,
                            String email,
                            String cpf,
                            String cidade,
                            String estado,
                            String autorizaUsoImagem,
                            String nomeAssina,
                            MultipartFile comprovante,
                            String ip) {
        var contrato = buscarPorToken(token);
        validarExpiracao(contrato);

        if (contrato.getStatus() != StatusContrato.PUBLICADO
            && contrato.getStatus() != StatusContrato.DEVOLVIDO) {
            throw new ContratoEstadoInvalidoException("PUBLICADO ou DEVOLVIDO", contrato.getStatus().name());
        }

        validarCamposCliente(nome, telefone, cpf, nomeAssina, autorizaUsoImagem);
        validarComprovante(comprovante);

        var autoriza = Boolean.parseBoolean(autorizaUsoImagem);
        var urlComprovante = fileStorageService.salvarEmSubdiretorio(
            comprovante, contrato.getId(), "comprovante_entrada");

        var dataAssinatura = LocalDateTime.now();

        var snapshotJson = montarSnapshot(contrato, nome, telefone, email, cpf, cidade,
            estado, autoriza, nomeAssina, dataAssinatura, ip, urlComprovante);
        var hash = GestaoContratoService.sha256(snapshotJson);

        var urlPdf = gravarPdf(contrato.getId(), montarTexto(contrato, nome, telefone, email, cpf,
            cidade, estado, autoriza, nomeAssina, dataAssinatura, ip, hash));

        var assinatura = Assinatura.builder()
            .contratoId(contrato.getId())
            .nomeAssinante(nomeAssina)
            .dataAssinatura(dataAssinatura)
            .ip(ip)
            .hash(hash)
            .build();
        assinaturaRepository.save(assinatura);

        contrato.setClienteNome(nome);
        contrato.setClienteTelefone(telefone);
        contrato.setClienteEmail(email);
        contrato.setClienteCpf(cpf);
        contrato.setClienteCidade(cidade);
        contrato.setClienteEstado(estado);
        contrato.setAutorizaUsoImagem(autoriza);
        contrato.setUrlComprovanteEntrada(urlComprovante);
        contrato.setSnapshotJson(snapshotJson);
        contrato.setSnapshotHash(hash);
        contrato.setUrlPdf(urlPdf);
        contrato.setDataAssinatura(dataAssinatura);
        contrato.setStatus(StatusContrato.ASSINADO_PELO_CLIENTE);
        contrato.setTipoMotivoDevolucao(null);
        contrato.setMotivoDevolucao(null);
        contrato.setDataDevolucao(null);
        contrato = contratoRepository.save(contrato);

        eventPublisher.publishEvent(new ContratoAssinadoEvent(contrato.getId()));

        return contrato;
    }

    private Contrato buscarPorToken(String token) {
        return contratoRepository.findByTokenHash(GestaoContratoService.sha256(token))
            .orElseThrow(() -> new ContratoNaoEncontradoException(token));
    }

    private void validarExpiracao(Contrato contrato) {
        boolean podeAssinar = contrato.getStatus() == StatusContrato.PUBLICADO
            || contrato.getStatus() == StatusContrato.DEVOLVIDO;
        if (podeAssinar
            && contrato.getTokenExpiracao() != null
            && contrato.getTokenExpiracao().isBefore(LocalDateTime.now())) {
            throw new ContratoTokenExpiradoException("token");
        }
    }

    private void validarCamposCliente(String nome, String telefone, String cpf,
                                      String nomeAssina, String autorizaUsoImagem) {
        if (isBlank(nome)) throw new IllegalArgumentException("Nome completo do cliente é obrigatório");
        if (isBlank(telefone)) throw new IllegalArgumentException("Telefone do cliente é obrigatório");
        if (isBlank(cpf)) throw new IllegalArgumentException("CPF do cliente é obrigatório");
        if (isBlank(nomeAssina)) throw new IllegalArgumentException("A assinatura (nome do contratante) é obrigatória");
        if (isBlank(autorizaUsoImagem)) throw new IllegalArgumentException("Selecione uma opção de uso de imagem");
    }

    private void validarComprovante(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Comprovante de pagamento da reserva é obrigatório");
        }
        var contentType = arquivo.getContentType();
        if (contentType == null || !List.of("application/pdf", "image/jpeg", "image/png").contains(contentType)) {
            throw new IllegalArgumentException("Tipo de arquivo inválido. Permitidos: PDF, JPG, PNG");
        }
    }

    private String montarSnapshot(Contrato c, String nome, String telefone, String email,
                                  String cpf, String cidade, String estado, boolean autoriza,
                                  String nomeAssina, LocalDateTime dataAssinatura, String ip,
                                   String urlComprovante) {
        var mapa = new LinkedHashMap<String, Object>();
        mapa.put("contratoId", c.getId());
        mapa.put("pacoteNome", c.getPacoteNome());
        mapa.put("valorPacote", c.getValorPacote().toPlainString());
        mapa.put("precoFotoExtra", c.getPrecoFotoExtra().toPlainString());
        mapa.put("dataHoraEnsaio", c.getDataHoraEnsaio().format(FMT_DATA_HORA));
        mapa.put("duracaoMinutos", c.getDuracaoMinutos());
        mapa.put("localEnsaio", c.getLocalEnsaio());
        mapa.put("enderecoCompleto", c.getEnderecoCompleto());
        mapa.put("percentualEntrada", c.getPercentualEntrada().toPlainString());
        mapa.put("valorTotal", c.getValorTotal().toPlainString());
        mapa.put("valorEntradaExigido", c.getValorEntradaExigido().toPlainString());
        mapa.put("valorRestante", c.getValorRestante().toPlainString());
        mapa.put("contratadaNome", configuracaoService.getValorTexto("nomeContratada", ""));
        mapa.put("contratadaCnpj", configuracaoService.getValorTexto("cnpjContratada", ""));
        mapa.put("contratadaCidade", configuracaoService.getValorTexto("enderecoContratada", ""));
        mapa.put("clienteNome", nome);
        mapa.put("clienteTelefone", telefone);
        mapa.put("clienteEmail", email);
        mapa.put("clienteCpf", cpf);
        mapa.put("clienteCidade", cidade);
        mapa.put("clienteEstado", estado);
        mapa.put("autorizaUsoImagem", autoriza);
        mapa.put("assinaturaNome", nomeAssina);
        mapa.put("dataAssinatura", dataAssinatura.format(FMT_DATA_HORA));
        mapa.put("ip", ip);
        mapa.put("urlComprovanteEntrada", urlComprovante);
        return toJson(mapa);
    }

    private String toJson(LinkedHashMap<String, Object> mapa) {
        var sb = new StringBuilder("{");
        var first = true;
        for (var entry : mapa.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append('"').append(escapeJson(entry.getKey())).append('"').append(':');
            var value = entry.getValue();
            if (value instanceof String s) {
                sb.append('"').append(escapeJson(s)).append('"');
            } else if (value instanceof Boolean || value instanceof Number) {
                sb.append(value);
            } else if (value == null) {
                sb.append("null");
            } else {
                sb.append('"').append(escapeJson(value.toString())).append('"');
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private String gravarPdf(java.util.UUID contratoId, List<String> linhas) {
        try {
            var dir = fileStorageService.getUploadDir().resolve("contratos");
            Files.createDirectories(dir);
            var destino = dir.resolve(contratoId + ".pdf");
            Files.write(destino, pdfWriter.gerar("PRESTAÇÃO DE SERVIÇOS FOTOGRÁFICOS", linhas));
            return destino.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao gerar PDF do contrato", e);
        }
    }

    private List<String> montarTexto(Contrato c, String nome, String telefone, String email,
                                     String cpf, String cidade, String estado, boolean autoriza,
                                     String nomeAssina, LocalDateTime dataAssinatura, String ip,
                                     String hash) {
        var linhas = new ArrayList<String>();
        linhas.add("");
        linhas.add("1. Dados do Cliente");
        linhas.add("Nome completo: " + segurar(nome));
        linhas.add("CPF: " + segurar(cpf));
        linhas.add("E-mail: " + segurar(email));
        linhas.add("Telefone: " + segurar(telefone));
        linhas.add("Cidade / Estado: " + segurar(cidade) + " / " + segurar(estado));
        linhas.add("");
        linhas.add("Contratada: " + configuracaoService.getValorTexto("nomeContratada", "")
            + ", inscrita no CNPJ nº " + configuracaoService.getValorTexto("cnpjContratada", "")
            + ", com sede em " + configuracaoService.getValorTexto("enderecoContratada", "") + ".");
        linhas.add("");
        linhas.add("2. Informações do Ensaio");
        linhas.add("Data do ensaio: " + c.getDataHoraEnsaio().format(FMT_DATA));
        linhas.add("Horário do ensaio: " + c.getDataHoraEnsaio().format(FMT_HORA));
        linhas.add("Local do ensaio: " + segurar(c.getLocalEnsaio()));
        if (c.getEnderecoCompleto() != null && !c.getEnderecoCompleto().isBlank()) {
            linhas.add("Endereço completo: " + c.getEnderecoCompleto());
        }
        linhas.add("");
        linhas.add("3. Pacote Contratado");
        linhas.add("Pacote: " + c.getPacoteNome());
        linhas.add("Inclui: serviço conforme pacote contratado.");
        linhas.add("");
        linhas.add("4. Valores");
        linhas.add("Valor total do serviço: R$ " + money(c.getValorTotal()));
        linhas.add("Valor pago como reserva (" + c.getPercentualEntrada().stripTrailingZeros().toPlainString()
            + "%): R$ " + money(c.getValorEntradaExigido()));
        linhas.add("Valor restante a pagar no final do ensaio: R$ " + money(c.getValorRestante()));
        linhas.add("O pagamento da reserva garante o bloqueio da data e horário na agenda da Contratada.");
        linhas.add("O valor restante deverá ser pago ao final da realização do ensaio fotográfico.");
        linhas.add("");
        linhas.add("Dados para pagamento (PIX)");
        linhas.add("Chave PIX (" + configuracaoService.getValorTexto("pixTipoChave", "CNPJ")
            + "): " + configuracaoService.getValorTexto("pixChave", ""));
        linhas.add("");
        linhas.add("5. Entrega das Fotografias");
        linhas.add("As fotos do ensaio serão enviadas ao Cliente em até 2 dias após a realização do ensaio para que ele faça a seleção das imagens desejadas.");
        linhas.add("Após a seleção, a entrega final das fotografias ocorrerá em até 2 dias.");
        linhas.add("As fotos serão entregues em formato digital, em alta resolução.");
        linhas.add("Caso o Cliente opte por fotos extras além do pacote contratado, será cobrado o valor de R$ "
            + money(c.getPrecoFotoExtra()) + " por foto adicional.");
        linhas.add("");
        linhas.add("6. Cancelamento");
        linhas.add("Caso o Cliente cancele o ensaio por qualquer motivo, o valor pago como reserva não será reembolsado, pois garante a reserva da data na agenda da Contratada.");
        linhas.add("Caso ocorra algum imprevisto que impeça a presença da Contratada, poderá haver a substituição por outro fotógrafo profissional de padrão equivalente.");
        linhas.add("Caso não seja possível a substituição, o valor pago será devolvido integralmente ao Cliente.");
        linhas.add("Se houver algum imprevisto relacionado à antecipação de voo, chuva ou doença, o ensaio será cancelado e haverá o reembolso completo do valor da reserva.");
        linhas.add("");
        linhas.add("7. USO DE IMAGEM (OPCIONAL)");
        linhas.add(autoriza ? "(X) AUTORIZO" : "( ) AUTORIZO");
        linhas.add(autoriza ? "( ) NÃO AUTORIZO" : "(X) NÃO AUTORIZO");
        linhas.add("");
        linhas.add("8. Disposições Gerais");
        linhas.add("Este contrato passa a vigorar a partir da assinatura das partes.");
        linhas.add("Qualquer alteração neste contrato deverá ser realizada por escrito.");
        linhas.add("");
        linhas.add("9. Assinatura Digital");
        linhas.add("Ao assinar este documento, o Cliente declara que leu e concorda com todos os termos acima descritos.");
        linhas.add("Resposta do cliente sobre uso de imagem: " + (autoriza ? "AUTORIZADO" : "NÃO autorizado"));
        linhas.add("");
        linhas.add("Assinatura do contratante: " + segurar(nomeAssina));
        linhas.add("Assinatura da Contratada: Carol Oliva Fotografia");
        linhas.add("");
        linhas.add("Assinado digitalmente em " + dataAssinatura.format(FMT_DATA_HORA)
            + " (IP " + segurar(ip) + ")");
        linhas.add("Hash do documento: " + hash);
        return linhas;
    }

    private String money(java.math.BigDecimal valor) {
        return valor == null ? "0,00" : valor.setScale(2, java.math.RoundingMode.HALF_UP)
            .toPlainString().replace(".", ",");
    }

    private String segurar(String valor) {
        return valor == null || valor.isBlank() ? "-" : valor;
    }

    private boolean isBlank(String valor) {
        return valor == null || valor.isBlank();
    }
}