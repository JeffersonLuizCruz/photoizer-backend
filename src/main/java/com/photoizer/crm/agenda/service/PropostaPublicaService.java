package com.photoizer.crm.agenda.service;

import tools.jackson.databind.ObjectMapper;
import com.photoizer.crm.agenda.api.PropostaPublicaResponse;
import com.photoizer.crm.agenda.api.PropostaStatusPublicoResponse;
import com.photoizer.crm.agenda.exception.PropostaNaoEncontradaException;
import com.photoizer.crm.agenda.exception.PropostaTokenExpiradoException;
import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.Assinatura;
import com.photoizer.crm.agenda.model.OrigemAgendamento;
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
import com.photoizer.crm.shared.storage.StoragePathResolver;
import com.photoizer.crm.shared.util.HashUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final TermoPdfComposer termoPdfComposer;
    private final ConfiguracaoService configuracaoService;
    private final PropostaTemplateService templateService;
    private final ObjectMapper objectMapper;
    private final StoragePathResolver pathResolver;

    public PropostaPublicaService(AgendamentoRepository agendamentoRepository,
                                  AgendamentoFotografoRepository agendamentoFotografoRepository,
                                  AssinaturaRepository assinaturaRepository,
                                  AgendamentoService agendamentoService,
                                  FileStorageService fileStorageService,
                                  FileValidator fileValidator,
                                  PdfWriter pdfWriter,
                                  TermoPdfComposer termoPdfComposer,
                                  ConfiguracaoService configuracaoService,
                                  PropostaTemplateService templateService,
                                  ObjectMapper objectMapper,
                                  StoragePathResolver pathResolver) {
        this.agendamentoRepository = agendamentoRepository;
        this.agendamentoFotografoRepository = agendamentoFotografoRepository;
        this.assinaturaRepository = assinaturaRepository;
        this.agendamentoService = agendamentoService;
        this.fileStorageService = fileStorageService;
        this.fileValidator = fileValidator;
        this.pdfWriter = pdfWriter;
        this.termoPdfComposer = termoPdfComposer;
        this.configuracaoService = configuracaoService;
        this.templateService = templateService;
        this.objectMapper = objectMapper;
        this.pathResolver = pathResolver;
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
            duracaoEnsaioLabel(agendamento),
            agendamento.getLocalEnsaio(),
            agendamento.getTaxaDeslocamento(),
            agendamento.getPercentualEntrada(),
            agendamento.getValorTotal(),
            agendamento.getValorEntradaExigido(),
            valorRestanteContrato(agendamento),
            html,
            nomeResponsavelEfetivo(agendamento),
            profissionais,
            exigeComprovante(agendamento),
            agendamento.getValorEntradaPago()
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
                               MultipartFile comprovante,
                               String userAgent,
                               String plataforma,
                               String fusoHorario,
                               String ip) {
        var agendamento = buscarPorToken(token);
        validarExpiracao(agendamento);

        var avulso = agendamento.getOrigem() == OrigemAgendamento.AVULSO;
        var avulsoPendenteDeAssinatura = avulso
            && agendamento.getStatus() == StatusAgendamento.CONFIRMADO
            && agendamento.getDataAssinatura() == null;
        if (agendamento.getStatus() != StatusAgendamento.PRE_RESERVA && !avulsoPendenteDeAssinatura) {
            throw new BadRequestException("Esta proposta não está disponível para assinatura.");
        }

        validarCamposCliente(nome, telefone, cpf, autorizaUsoImagem);
        if (exigeComprovante(agendamento)) {
            validarComprovante(comprovante);
        }

        var cliente = agendamentoService.resolverCliente(nome, telefone, email, cpf, cidade, estado);

        String urlComprovante = null;
        if (comprovante != null && !comprovante.isEmpty()) {
            fileValidator.validate(comprovante, "receipt");
            urlComprovante = fileStorageService.salvarEmSubdiretorio(
                comprovante, agendamento.getId(), "comprovante_entrada");
        }

        // Sem assinatura manuscrita: o nome do contratante é a prova de aceite (nome informado no passo de dados).
        var assinanteNome = assinanteNome(nome);
        String urlAssinaturaImagem = null; // assinatura manuscrita removida do fluxo público
        var dataAssinatura = LocalDateTime.now();

        var snapshotJson = montarSnapshot(agendamento, nome, telefone, email, cpf, cidade, estado,
            autorizaUsoImagem, assinanteNome, dataAssinatura, ip, userAgent, plataforma, fusoHorario,
            urlComprovante, urlAssinaturaImagem);
        var hash = HashUtils.sha256(snapshotJson);

        var placeholders = montarPlaceholders(agendamento, nome, telefone, email, cpf,
            cidade, estado, "true".equalsIgnoreCase(autorizaUsoImagem));
        var template = templateService.carregarTemplate();
        var texto = (template == null || template.isBlank())
            ? "Termo sem template definido."
            : templateService.renderizarTexto(template, placeholders);
        var documento = termoPdfComposer.compor(agendamento, placeholders, texto,
            new TermoPdfComposer.DadosAssinatura(assinanteNome, dataAssinatura, ip, hash));

        var urlPdf = gravarPdf(agendamento.getId(), documento);

        assinaturaRepository.findByAgendamentoId(agendamento.getId())
            .ifPresent(assinaturaRepository::delete);

        var assinatura = Assinatura.builder()
            .agendamentoId(agendamento.getId())
            .nomeAssinante(assinanteNome)
            .dataAssinatura(dataAssinatura)
            .ip(ip)
            .hash(hash)
            .userAgent(userAgent)
            .plataforma(plataforma)
            .fusoHorario(fusoHorario)
            .urlAssinaturaImagem(urlAssinaturaImagem)
            .build();
        assinaturaRepository.save(assinatura);

        if (avulso) {
            agendamento.registrarAssinaturaContrato(cliente, autorizaUsoImagem, urlComprovante,
                assinanteNome, snapshotJson, hash, urlPdf, urlAssinaturaImagem);
        } else {
            agendamento.assinarProposta(cliente, autorizaUsoImagem, urlComprovante, assinanteNome,
                snapshotJson, hash, urlPdf, urlAssinaturaImagem);
        }

        return agendamentoRepository.save(agendamento);
    }

    /** Nome do contratante usado como assinante; truncado ao limite da coluna (64). */
    private String assinanteNome(String nome) {
        if (nome == null) return null;
        return nome.length() > 64 ? nome.substring(0, 64) : nome;
    }

    private boolean podeAssinar(Agendamento a) {
        if (a.getStatus() == StatusAgendamento.PRE_RESERVA) {
            return true;
        }
        return a.getOrigem() == OrigemAgendamento.AVULSO
            && a.getStatus() == StatusAgendamento.CONFIRMADO
            && a.getDataAssinatura() == null;
    }

    /**
     * O avulso já nasce confirmado e a reserva pode ter sido recebida pelo staff;
     * nesse fluxo o link público coleta apenas os dados e o aceite do cliente.
     * Propostas comuns também dispensam o comprovante quando a entrada já foi
     * integralmente recebida pelo staff.
     */
    private boolean exigeComprovante(Agendamento a) {
        if (a.getOrigem() == OrigemAgendamento.AVULSO) {
            return false;
        }
        return !a.entradaQuitada();
    }

    /**
     * Status exibido no contrato para a reserva. Propostas comuns mantêm a
     * semântica anterior ("valor pago como reserva", pois o cliente anexa o
     * comprovante ao assinar); no avulso o status depende de a reserva já ter
     * sido recebida pelo staff.
     */
    String statusReserva(Agendamento a) {
        if (a.getOrigem() == OrigemAgendamento.AVULSO
            && (a.getValorEntradaPago() == null || a.getValorEntradaPago().signum() == 0)) {
            return "a receber";
        }
        return "recebida";
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
        var status = agendamento.getStatus();
        var propostaEmAnalise = status == StatusAgendamento.PRE_RESERVA
            || status == StatusAgendamento.AGUARDANDO_APROVACAO
            || (agendamento.getOrigem() == OrigemAgendamento.AVULSO
                && status == StatusAgendamento.CONFIRMADO
                && agendamento.getDataAssinatura() == null);
        if (propostaEmAnalise
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
        var pacoteDescricao = (pacote.getDescricao() == null || pacote.getDescricao().isBlank())
            ? "serviço conforme pacote contratado."
            : neutralizarMarcacaoBloco(pacote.getDescricao());

        var autorizaTexto = autoriza ? "(X) AUTORIZO\n( ) NÃO AUTORIZO" : "( ) AUTORIZO\n(X) NÃO AUTORIZO";

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
            dataHora.format(FMT_DATA), dataHora.format(FMT_HORA), duracaoEnsaioLabel(a),
            a.getLocalEnsaio(),
            pacote.getNome(),
            pacoteDescricao,
            "R$ " + money(pacote.getPrecoFotoExtra()),
            "R$ " + money(a.getValorTotal()),
            "R$ " + money(a.getValorEntradaExigido()),
            statusReserva(a),
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

    /**
     * Evita que a descrição do pacote (texto livre) seja interpretada como
     * marcação de template pelo renderizador de HTML do contrato: linhas que
     * iniciam por {@code "# "} ou {@code "= "} seriam convertidas em cabeçalhos.
     * Prefixa essas linhas com NBSP, que sobrevive ao {@code trim()} e é
     * renderizado como espaço tanto no HTML quanto no PDF. Linhas iniciadas
     * por {@code "- "} são preservadas como itens de lista.
     */
    private static String neutralizarMarcacaoBloco(String texto) {
        var linhas = texto.split("\n", -1);
        var sb = new StringBuilder();
        for (int i = 0; i < linhas.length; i++) {
            var linha = linhas[i];
            var trim = linha.stripLeading();
            if (trim.startsWith("# ") || trim.startsWith("= ")) {
                linha = "\u00A0" + linha;
            }
            sb.append(linha);
            if (i < linhas.length - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String montarSnapshot(Agendamento a, String nome, String telefone, String email,
                                  String cpf, String cidade, String estado, String autorizaUsoImagem,
                                  String assinante, LocalDateTime dataAssinatura, String ip,
                                  String userAgent, String plataforma, String fusoHorario,
                                  String urlComprovante, String urlAssinaturaImagem) {
        var mapa = new LinkedHashMap<String, Object>();
        mapa.put("agendamentoId", a.getId());
        mapa.put("pacoteNome", a.getPacote().getNome());
        mapa.put("pacoteDescricao", a.getPacote().getDescricao());
        mapa.put("dataHoraEnsaio", a.getDataHoraEnsaio().format(FMT_DATA_HORA));
        mapa.put("duracaoMinutos", a.getDuracaoMinutos());
        mapa.put("localEnsaio", a.getLocalEnsaio());
        mapa.put("percentualEntrada", a.getPercentualEntrada().toPlainString());
        mapa.put("valorTotal", a.getValorTotal().toPlainString());
        mapa.put("valorEntradaExigido", a.getValorEntradaExigido().toPlainString());
        mapa.put("valorEntradaPago", a.getValorEntradaPago().toPlainString());
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

    private String gravarPdf(UUID agendamentoId, com.photoizer.crm.shared.pdf.PdfDocument documento) {
        try {
            var dir = fileStorageService.getUploadDir().resolve("propostas");
            Files.createDirectories(dir);
            var destino = dir.resolve(agendamentoId + ".pdf");
            Files.write(destino, pdfWriter.gerar(documento));
            return pathResolver.relativo(destino);
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao gerar PDF da proposta", e);
        }
    }

    /**
     * Rótulo legível da duração do ensaio exibido na proposta e no contrato.
     * Pacotes que bloqueiam o dia inteiro exibem "Dia inteiro"; caso contrário,
     * formata o snapshot {@code duracaoMinutos} gravado no agendamento (ex.:
     * "2h", "2h30", "45 min"), para que contratos já emitidos não mudem quando
     * a duração do pacote for editada posteriormente.
     */
    private String duracaoEnsaioLabel(Agendamento a) {
        var pacote = a.getPacote();
        if (pacote != null && Boolean.TRUE.equals(pacote.getBloqueiaDiaInteiro())) {
            return "Dia inteiro";
        }
        var minutos = a.getDuracaoMinutos();
        if (minutos == null || minutos <= 0) {
            return "—";
        }
        if (minutos < 60) {
            return minutos + " min";
        }
        var horas = minutos / 60;
        var resto = minutos % 60;
        return resto == 0 ? horas + "h" : horas + "h" + resto;
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

    private void validarCamposCliente(String nome, String telefone, String cpf, String autorizaUsoImagem) {
        if (isBlank(nome)) throw new BadRequestException("Nome completo do cliente é obrigatório");
        if (isBlank(telefone)) throw new BadRequestException("Telefone do cliente é obrigatório");
        if (isBlank(cpf)) throw new BadRequestException("CPF do cliente é obrigatório");
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
