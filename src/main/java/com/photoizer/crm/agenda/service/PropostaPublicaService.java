package com.photoizer.crm.agenda.service;

import tools.jackson.databind.ObjectMapper;
import com.photoizer.crm.agenda.api.PropostaPublicaResponse;
import com.photoizer.crm.agenda.api.PropostaStatusPublicoResponse;
import com.photoizer.crm.agenda.exception.PropostaNaoEncontradaException;
import com.photoizer.crm.agenda.exception.PropostaTokenExpiradoException;
import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.Assinatura;
import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.agenda.repository.AssinaturaRepository;
import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.shared.exception.BadRequestException;
import com.photoizer.crm.shared.pdf.PdfWriter;
import com.photoizer.crm.shared.storage.FileStorageService;
import com.photoizer.crm.shared.storage.FileValidator;
import com.photoizer.crm.shared.util.HashUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fluxo público de assinatura da proposta. O cliente se auto-cadastra,
 * escolhe autorização de imagem, anexa o comprovante, assina (nome + desenho)
 * e o agendamento sai de PRE_RESERVA para AGUARDANDO_APROVACAO.
 */
@Service
@Transactional
public class PropostaPublicaService {

    private static final DateTimeFormatter FMT_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final AgendamentoRepository agendamentoRepository;
    private final AgendamentoFotografoRepository agendamentoFotografoRepository;
    private final AssinaturaRepository assinaturaRepository;
    private final AgendamentoService agendamentoService;
    private final FileStorageService fileStorageService;
    private final FileValidator fileValidator;
    private final PdfWriter pdfWriter;
    private final ConfiguracaoService configuracaoService;
    private final PropostaTemplateService templateService;
    private final ObjectMapper objectMapper;

    public PropostaPublicaService(AgendamentoRepository agendamentoRepository,
                                  AgendamentoFotografoRepository agendamentoFotografoRepository,
                                  AssinaturaRepository assinaturaRepository,
                                  AgendamentoService agendamentoService,
                                  FileStorageService fileStorageService,
                                  FileValidator fileValidator,
                                  PdfWriter pdfWriter,
                                  ConfiguracaoService configuracaoService,
                                  PropostaTemplateService templateService,
                                  ObjectMapper objectMapper) {
        this.agendamentoRepository = agendamentoRepository;
        this.agendamentoFotografoRepository = agendamentoFotografoRepository;
        this.assinaturaRepository = assinaturaRepository;
        this.agendamentoService = agendamentoService;
        this.fileStorageService = fileStorageService;
        this.fileValidator = fileValidator;
        this.pdfWriter = pdfWriter;
        this.configuracaoService = configuracaoService;
        this.templateService = templateService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PropostaPublicaResponse buscarPublico(String token) {
        var agendamento = buscarPorToken(token);
        validarExpiracao(agendamento);

        var html = renderizarClausulasHtml(agendamento);

        var profissionais = agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(agendamento.getId())
            .stream()
            .filter(cf -> cf.getStatus() != RepasseStatus.CANCELADO)
            .map(cf -> new PropostaPublicaResponse.ProfissionalEnsaio(
                cf.getFotografo().getNome(),
                cf.getPapelParceiro() != null ? rotuloPapel(cf.getPapelParceiro()) : null))
            .toList();

        var pacote = agendamento.getPacote();
        return new PropostaPublicaResponse(
            agendamento.getStatus().name(),
            podeAssinar(agendamento),
            configuracaoService.getValor(ConfigKey.NOME_CONTRATADA),
            configuracaoService.getValor(ConfigKey.CNPJ_CONTRATADA),
            configuracaoService.getValor(ConfigKey.ENDERECO_CONTRATADA),
            configuracaoService.getValor(ConfigKey.PIX_CHAVE),
            configuracaoService.getValor(ConfigKey.PIX_TIPO_CHAVE),
            pacote.getNome(),
            pacote.getValorBase(),
            pacote.getPrecoFotoExtra(),
            agendamento.getDataHoraEnsaio(),
            agendamento.getDuracaoMinutos(),
            agendamento.getLocalEnsaio(),
            agendamento.getTaxaDeslocamento(),
            agendamento.getPercentualEntrada(),
            agendamento.getValorTotal(),
            agendamento.getValorEntradaExigido(),
            valorRestanteContrato(agendamento),
            html,
            nomeResponsavelEfetivo(agendamento),
            profissionais
        );
    }

    @Transactional(readOnly = true)
    public PropostaStatusPublicoResponse status(String token) {
        var agendamento = buscarPorToken(token);
        validarExpiracao(agendamento);
        return PropostaStatusPublicoResponse.of(agendamento);
    }

    public Agendamento assinar(String token,
                               String nome,
                               String telefone,
                               String email,
                               String cpf,
                               String cidade,
                               String estado,
                               String autorizaUsoImagem,
                               String assinaturaNome,
                               MultipartFile comprovante,
                               MultipartFile assinaturaImagem,
                               String userAgent,
                               String plataforma,
                               String fusoHorario,
                               String ip) {
        var agendamento = buscarPorToken(token);
        validarExpiracao(agendamento);

        if (agendamento.getStatus() != StatusAgendamento.PRE_RESERVA) {
            throw new BadRequestException("Esta proposta não está disponível para assinatura.");
        }

        validarCamposCliente(nome, telefone, cpf, assinaturaNome, autorizaUsoImagem);
        validarComprovante(comprovante);
        if (assinaturaImagem == null || assinaturaImagem.isEmpty()) {
            throw new BadRequestException("A assinatura (desenho) é obrigatória");
        }

        var cliente = agendamentoService.resolverCliente(nome, telefone, email, cpf, cidade, estado);

        fileValidator.validate(comprovante, "receipt");
        var urlComprovante = fileStorageService.salvarEmSubdiretorio(
            comprovante, agendamento.getId(), "comprovante_entrada");

        fileValidator.validate(assinaturaImagem, "image");
        var urlAssinaturaImagem = fileStorageService.salvarEmSubdiretorio(
            assinaturaImagem, agendamento.getId(), "assinatura");

        var dataAssinatura = LocalDateTime.now();

        var snapshotJson = montarSnapshot(agendamento, nome, telefone, email, cpf, cidade, estado,
            autorizaUsoImagem, assinaturaNome, dataAssinatura, ip, userAgent, plataforma, fusoHorario,
            urlComprovante, urlAssinaturaImagem);
        var hash = HashUtils.sha256(snapshotJson);

        var urlPdf = gravarPdf(agendamento.getId(), montarTexto(agendamento, nome, telefone, email, cpf,
            cidade, estado, autorizaUsoImagem, assinaturaNome, dataAssinatura, ip, hash, urlAssinaturaImagem));

        assinaturaRepository.findByAgendamentoId(agendamento.getId())
            .ifPresent(assinaturaRepository::delete);

        var assinatura = Assinatura.builder()
            .agendamentoId(agendamento.getId())
            .nomeAssinante(assinaturaNome)
            .dataAssinatura(dataAssinatura)
            .ip(ip)
            .hash(hash)
            .userAgent(userAgent)
            .plataforma(plataforma)
            .fusoHorario(fusoHorario)
            .urlAssinaturaImagem(urlAssinaturaImagem)
            .build();
        assinaturaRepository.save(assinatura);

        agendamento.assinarProposta(cliente, autorizaUsoImagem, urlComprovante, assinaturaNome,
            snapshotJson, hash, urlPdf, urlAssinaturaImagem);

        return agendamentoRepository.save(agendamento);
    }

    private boolean podeAssinar(Agendamento a) {
        return a.getStatus() == StatusAgendamento.PRE_RESERVA;
    }

    /**
     * Nome do fotógrafo responsável do ensaio. Usa o responsável selecionado na
     * proposta e, na ausência, o "Nome do Fotógrafo" configurado.
     */
    private String nomeResponsavelEfetivo(Agendamento a) {
        var responsavel = a.getFotografo();
        if (responsavel != null && responsavel.getNome() != null && !responsavel.getNome().isBlank()) {
            return responsavel.getNome();
        }
        return configuracaoService.getValor(ConfigKey.NOME_FOTOGRAFO);
    }

    /**
     * Rótulo legível do papel do profissional para exibição no contrato.
     */
    private String rotuloPapel(Papel papel) {
        return switch (papel) {
            case ADMIN -> "Administrador";
            case FOTOGRAFO -> "Fotógrafo";
            case EDITOR -> "Editor";
            case AGENDADOR -> "Agendador";
        };
    }

    private Agendamento buscarPorToken(String token) {
        return agendamentoRepository.findByTokenPropostaHash(HashUtils.sha256(token))
            .orElseThrow(() -> new PropostaNaoEncontradaException(token));
    }

    private void validarExpiracao(Agendamento agendamento) {
        if (agendamento.getStatus() == StatusAgendamento.PRE_RESERVA
            && agendamento.getTokenPropostaExpiracao() != null
            && agendamento.getTokenPropostaExpiracao().isBefore(LocalDateTime.now())) {
            throw new PropostaTokenExpiradoException();
        }
    }

    private String renderizarClausulasHtml(Agendamento a) {
        var template = templateService.carregarTemplate();
        if (template == null) return "";
        var vals = montarPlaceholders(a, null, null, null, null, null, null, false);
        return templateService.renderizarHtmlPublico(template, vals);
    }

    private Map<String, String> montarPlaceholders(Agendamento a, String nome, String telefone, String email,
                                                   String cpf, String cidade, String estado, boolean autoriza) {
        var dataHora = a.getDataHoraEnsaio();
        var pacote = a.getPacote();

        var autorizaTexto = autoriza ? "(X) AUTORIZO\n( ) NÃO AUTORIZO" : "( ) AUTORIZO\n( ) NÃO AUTORIZO";

        var blocoProfissionais = agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(a.getId()).stream()
            .filter(cf -> cf.getStatus() != RepasseStatus.CANCELADO)
            .map(cf -> "- " + cf.getFotografo().getNome()
                + (cf.getPapelParceiro() != null ? " (" + rotuloPapel(cf.getPapelParceiro()) + ")" : ""))
            .collect(java.util.stream.Collectors.joining("\n"));
        var profissionaisEnsaio = blocoProfissionais.isBlank()
            ? ""
            : "Profissionais do ensaio:\n" + blocoProfissionais;

        return templateService.buildPlaceholders(
            nome, cpf, telefone, email, cidade, estado,
            dataHora.format(FMT_DATA), dataHora.format(FMT_HORA),
            a.getLocalEnsaio(),
            pacote.getNome(),
            "R$ " + money(pacote.getPrecoFotoExtra()),
            "R$ " + money(a.getValorTotal()),
            "R$ " + money(a.getValorEntradaExigido()),
            a.getPercentualEntrada().stripTrailingZeros().toPlainString(),
            "R$ " + money(valorRestanteContrato(a)),
            configuracaoService.getValor(ConfigKey.NOME_CONTRATADA),
            configuracaoService.getValor(ConfigKey.CNPJ_CONTRATADA),
            configuracaoService.getValor(ConfigKey.ENDERECO_CONTRATADA),
            configuracaoService.getValor(ConfigKey.PIX_CHAVE),
            configuracaoService.getValor(ConfigKey.PIX_TIPO_CHAVE),
            autorizaTexto,
            "R$ " + money(a.getTaxaDeslocamento()),
            configuracaoService.getValor(ConfigKey.NOME_FOTOGRAFO),
            profissionaisEnsaio
        );
    }

    private String montarSnapshot(Agendamento a, String nome, String telefone, String email,
                                  String cpf, String cidade, String estado, String autorizaUsoImagem,
                                  String assinante, LocalDateTime dataAssinatura, String ip,
                                  String userAgent, String plataforma, String fusoHorario,
                                  String urlComprovante, String urlAssinaturaImagem) {
        var mapa = new LinkedHashMap<String, Object>();
        mapa.put("agendamentoId", a.getId());
        mapa.put("pacoteNome", a.getPacote().getNome());
        mapa.put("dataHoraEnsaio", a.getDataHoraEnsaio().format(FMT_DATA_HORA));
        mapa.put("duracaoMinutos", a.getDuracaoMinutos());
        mapa.put("localEnsaio", a.getLocalEnsaio());
        mapa.put("percentualEntrada", a.getPercentualEntrada().toPlainString());
        mapa.put("valorTotal", a.getValorTotal().toPlainString());
        mapa.put("valorEntradaExigido", a.getValorEntradaExigido().toPlainString());
        mapa.put("valorRestante", valorRestanteContrato(a).toPlainString());
        mapa.put("clienteNome", nome);
        mapa.put("clienteTelefone", telefone);
        mapa.put("clienteEmail", email);
        mapa.put("clienteCpf", cpf);
        mapa.put("clienteCidade", cidade);
        mapa.put("clienteEstado", estado);
        mapa.put("autorizaUsoImagem", "true".equalsIgnoreCase(autorizaUsoImagem));
        mapa.put("assinaturaNome", assinante);
        mapa.put("dataAssinatura", dataAssinatura.format(FMT_DATA_HORA));
        mapa.put("ip", ip);
        mapa.put("userAgent", userAgent);
        mapa.put("plataforma", plataforma);
        mapa.put("fusoHorario", fusoHorario);
        mapa.put("urlComprovanteEntrada", urlComprovante);
        mapa.put("urlAssinaturaImagem", urlAssinaturaImagem);
        try {
            return objectMapper.writeValueAsString(mapa);
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalStateException("Erro ao serializar snapshot da proposta", e);
        }
    }

    private String gravarPdf(UUID agendamentoId, List<String> linhas) {
        try {
            var dir = fileStorageService.getUploadDir().resolve("propostas");
            Files.createDirectories(dir);
            var destino = dir.resolve(agendamentoId + ".pdf");
            Files.write(destino, pdfWriter.gerar("PRESTAÇÃO DE SERVIÇOS FOTOGRÁFICOS", linhas));
            return destino.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao gerar PDF da proposta", e);
        }
    }

    private List<String> montarTexto(Agendamento a, String nome, String telefone, String email,
                                     String cpf, String cidade, String estado, String autoriza,
                                     String assinante, LocalDateTime dataAssinatura, String ip,
                                     String hash, String urlAssinaturaImagem) {
        var template = templateService.carregarTemplate();
        if (template == null || template.isBlank()) {
            return List.of("Termo sem template definido.");
        }
        var vals = montarPlaceholders(a, nome, telefone, email, cpf, cidade, estado,
            "true".equalsIgnoreCase(autoriza));
        var texto = templateService.renderizarTexto(template, vals);
        var linhas = new ArrayList<>(List.of(texto.split("\n", -1)));
        linhas.add("");
        linhas.add("Assinado digitalmente em " + dataAssinatura.format(FMT_DATA_HORA)
            + " (IP " + segurar(ip) + ")");
        linhas.add("Hash do documento: " + hash);
        return linhas;
    }

    /**
     * Valor a pagar ao final do ensaio conforme o contrato: total final menos a
     * reserva exigida. Independe de a reserva já ter sido efetivamente paga,
     * ao contrário de {@code Agendamento.getValorRestante()} (que é o "a receber").
     */
    private BigDecimal valorRestanteContrato(Agendamento a) {
        return a.getValorTotalFinal().subtract(a.getValorEntradaExigido()).max(BigDecimal.ZERO);
    }

    private String money(java.math.BigDecimal valor) {
        return valor == null ? "0,00" : valor.setScale(2, java.math.RoundingMode.HALF_UP)
            .toPlainString().replace(".", ",");
    }

    private String segurar(String valor) {
        return valor == null || valor.isBlank() ? "-" : valor;
    }

    private void validarCamposCliente(String nome, String telefone, String cpf,
                                      String assinaturaNome, String autorizaUsoImagem) {
        if (isBlank(nome)) throw new BadRequestException("Nome completo do cliente é obrigatório");
        if (isBlank(telefone)) throw new BadRequestException("Telefone do cliente é obrigatório");
        if (isBlank(cpf)) throw new BadRequestException("CPF do cliente é obrigatório");
        if (isBlank(assinaturaNome)) throw new BadRequestException("A assinatura (nome do contratante) é obrigatória");
        if (isBlank(autorizaUsoImagem)) throw new BadRequestException("Selecione uma opção de uso de imagem");
    }

    private void validarComprovante(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new BadRequestException("Comprovante de pagamento da reserva é obrigatório");
        }
        var contentType = arquivo.getContentType();
        if (contentType == null || !List.of("application/pdf", "image/jpeg", "image/png").contains(contentType)) {
            throw new BadRequestException("Tipo de arquivo inválido. Permitidos: PDF, JPG, PNG");
        }
    }

    private boolean isBlank(String valor) {
        return valor == null || valor.isBlank();
    }
}
