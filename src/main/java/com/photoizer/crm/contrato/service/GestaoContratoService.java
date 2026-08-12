package com.photoizer.crm.contrato.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoizer.crm.agenda.exception.AgendamentoNoPassadoException;
import com.photoizer.crm.agenda.exception.EditorNaoEncontradoException;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.contrato.api.CriarContratoRequest;
import com.photoizer.crm.contrato.api.DevolverContratoRequest;
import com.photoizer.crm.contrato.api.PublicarContratoResponse;
import com.photoizer.crm.contrato.event.ContratoAprovadoEvent;
import com.photoizer.crm.contrato.event.ContratoAssinadoEvent;
import com.photoizer.crm.contrato.event.ContratoDevolvidoEvent;
import com.photoizer.crm.contrato.exception.ContratoEstadoInvalidoException;
import com.photoizer.crm.contrato.exception.ContratoNaoEncontradoException;
import com.photoizer.crm.contrato.model.Contrato;
import com.photoizer.crm.contrato.model.StatusContrato;
import com.photoizer.crm.contrato.repository.ContratoRepository;
import com.photoizer.crm.pacote.exception.PacoteInativoException;
import com.photoizer.crm.pacote.exception.PacoteNaoEncontradoException;
import com.photoizer.crm.pacote.repository.PacoteRepository;
import com.photoizer.crm.auth.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class GestaoContratoService {

    private static final int DIAS_VALIDADE_PADRAO = 7;

    private final ContratoRepository contratoRepository;
    private final PacoteRepository pacoteRepository;
    private final UserRepository userRepository;
    private final ConfiguracaoService configuracaoService;
    private final ApplicationEventPublisher eventPublisher;

    public GestaoContratoService(ContratoRepository contratoRepository,
                           PacoteRepository pacoteRepository,
                           UserRepository userRepository,
                           ConfiguracaoService configuracaoService,
                           ApplicationEventPublisher eventPublisher) {
        this.contratoRepository = contratoRepository;
        this.pacoteRepository = pacoteRepository;
        this.userRepository = userRepository;
        this.configuracaoService = configuracaoService;
        this.eventPublisher = eventPublisher;
    }

    public Contrato criar(CriarContratoRequest request) {
        var pacote = pacoteRepository.findById(request.pacoteId())
            .orElseThrow(() -> new PacoteNaoEncontradoException(request.pacoteId()));

        if (!pacote.getAtivo()) {
            throw new PacoteInativoException(pacote.getId());
        }

        if (request.editorId() != null) {
            userRepository.findById(request.editorId())
                .orElseThrow(() -> new EditorNaoEncontradoException(request.editorId()));
        }

        if (request.dataHoraEnsaio().isBefore(LocalDateTime.now())) {
            throw new AgendamentoNoPassadoException();
        }

        var custoDeslocamento = request.custoDeslocamento() != null
            ? request.custoDeslocamento()
            : configuracaoService.getValorDecimal("taxaDeslocamentoPadrao", BigDecimal.ZERO);
        var repassarDeslocamento = request.repassarDeslocamento() != null
            ? request.repassarDeslocamento()
            : true;
        var taxaDeslocamento = repassarDeslocamento ? custoDeslocamento : BigDecimal.ZERO;

        var percentualEntrada = configuracaoService.getValorDecimal("percentualEntrada", new BigDecimal("30.00"));
        var fatorEntrada = percentualEntrada.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        var valorTotal = pacote.getValorBase().add(taxaDeslocamento);
        var valorEntradaExigido = valorTotal.multiply(fatorEntrada).setScale(2, RoundingMode.HALF_UP);
        var valorRestante = valorTotal.subtract(valorEntradaExigido);

        var contrato = Contrato.builder()
            .status(StatusContrato.RASCUNHO)
            .pacoteId(pacote.getId())
            .pacoteNome(pacote.getNome())
            .valorPacote(pacote.getValorBase())
            .precoFotoExtra(pacote.getPrecoFotoExtra())
            .editorId(request.editorId())
            .dataHoraEnsaio(request.dataHoraEnsaio())
            .duracaoMinutos(request.duracaoMinutos() != null ? request.duracaoMinutos() : 60)
            .localEnsaio(request.localEnsaio())
            .enderecoCompleto(request.enderecoCompleto())
            .custoDeslocamento(custoDeslocamento)
            .repassarDeslocamento(repassarDeslocamento)
            .taxaDeslocamento(taxaDeslocamento)
            .percentualEntrada(percentualEntrada)
            .valorTotal(valorTotal)
            .valorEntradaExigido(valorEntradaExigido)
            .valorRestante(valorRestante)
            .clienteId(request.clienteId())
            .observacoes(request.observacoes())
            .indicadorId(request.indicadorId())
            .indicadorNome(request.indicadorNome())
            .indicadorTelefone(request.indicadorTelefone())
            .build();

        return contratoRepository.save(contrato);
    }

    public PublicarContratoResponse publicar(UUID id) {
        var contrato = buscar(id);

        if (contrato.getStatus() != StatusContrato.RASCUNHO
            && contrato.getStatus() != StatusContrato.PUBLICADO
            && contrato.getStatus() != StatusContrato.CANCELADO
            && contrato.getStatus() != StatusContrato.EXPIRADO) {
            throw new ContratoEstadoInvalidoException("RASCUNHO, PUBLICADO, CANCELADO ou EXPIRADO",
                contrato.getStatus().name());
        }

        var token = UUID.randomUUID().toString();
        var diasValidade = configuracaoService.getValorInteiro("contratoDiasValidade", DIAS_VALIDADE_PADRAO);

        contrato.setToken(token);
        contrato.setTokenHash(sha256(token));
        contrato.setStatus(StatusContrato.PUBLICADO);
        contrato.setPublicadoEm(LocalDateTime.now());
        contrato.setTokenExpiracao(LocalDateTime.now().plusDays(diasValidade));
        contratoRepository.save(contrato);

        return new PublicarContratoResponse(contrato.getId(), "/contrato/" + token);
    }

    @Transactional(readOnly = true)
    public Contrato buscar(UUID id) {
        return contratoRepository.findById(id)
            .orElseThrow(() -> new ContratoNaoEncontradoException(id));
    }

    @Transactional(readOnly = true)
    public List<Contrato> listar(StatusContrato status, String search) {
        var contratos = status != null
            ? contratoRepository.findByStatus(status)
            : contratoRepository.findAll();
        if (search == null || search.isBlank()) {
            return contratos;
        }
        var termo = search.trim().toLowerCase();
        return contratos.stream()
            .filter(c -> (c.getClienteNome() != null && c.getClienteNome().toLowerCase().contains(termo))
                || (c.getClienteTelefone() != null && c.getClienteTelefone().toLowerCase().contains(termo))
                || c.getPacoteNome().toLowerCase().contains(termo))
            .sorted(Comparator.comparing(Contrato::getCreatedAt).reversed())
            .toList();
    }

    public Contrato confirmarPagamento(UUID id) {
        var contrato = buscar(id);
        if (contrato.getStatus() != StatusContrato.ASSINADO_PELO_CLIENTE) {
            throw new ContratoEstadoInvalidoException("ASSINADO_PELO_CLIENTE", contrato.getStatus().name());
        }
        contrato.setStatus(StatusContrato.PAGAMENTO_CONFIRMADO);
        contrato.setDataPagamentoConfirmado(LocalDateTime.now());
        return contratoRepository.save(contrato);
    }

    public Contrato aprovar(UUID id) {
        var contrato = buscar(id);
        if (contrato.getStatus() != StatusContrato.PAGAMENTO_CONFIRMADO) {
            throw new ContratoEstadoInvalidoException("PAGAMENTO_CONFIRMADO", contrato.getStatus().name());
        }
        contrato.setStatus(StatusContrato.APROVADO);
        contrato.setDataAprovacao(LocalDateTime.now());
        contratoRepository.save(contrato);

        eventPublisher.publishEvent(new ContratoAprovadoEvent(
            contrato.getId(),
            contrato.getClienteId(),
            contrato.getClienteNome(),
            contrato.getClienteTelefone(),
            contrato.getClienteEmail(),
            contrato.getClienteCpf(),
            contrato.getClienteCidade(),
            contrato.getClienteEstado(),
            contrato.getPacoteId(),
            contrato.getEditorId(),
            contrato.getDataHoraEnsaio(),
            contrato.getDuracaoMinutos(),
            contrato.getLocalEnsaio(),
            contrato.getEnderecoCompleto(),
            contrato.getValorTotal(),
            contrato.getValorEntradaExigido(),
            contrato.getPercentualEntrada(),
            contrato.getUrlComprovanteEntrada(),
            contrato.getAutorizaUsoImagem(),
            contrato.getObservacoes(),
            contrato.getIndicadorId(),
            contrato.getIndicadorNome(),
            contrato.getIndicadorTelefone(),
            contrato.getValorPacote(),
            contrato.getCustoDeslocamento(),
            contrato.getRepassarDeslocamento()
        ));

        return contrato;
    }

    public Contrato devolver(UUID id, DevolverContratoRequest request) {
        var contrato = buscar(id);
        if (contrato.getStatus() != StatusContrato.ASSINADO_PELO_CLIENTE
            && contrato.getStatus() != StatusContrato.PAGAMENTO_CONFIRMADO) {
            throw new ContratoEstadoInvalidoException("ASSINADO_PELO_CLIENTE ou PAGAMENTO_CONFIRMADO",
                contrato.getStatus().name());
        }
        contrato.setStatus(StatusContrato.DEVOLVIDO);
        contrato.setDataDevolucao(LocalDateTime.now());
        contrato.setTipoMotivoDevolucao(request.tipoMotivo());
        contrato.setMotivoDevolucao(request.motivo());
        contratoRepository.save(contrato);

        eventPublisher.publishEvent(new ContratoDevolvidoEvent(
            contrato.getId(), request.tipoMotivo(), request.motivo()));

        return contrato;
    }

    public Contrato cancelar(UUID id) {
        var contrato = buscar(id);
        if (contrato.getStatus() == StatusContrato.APROVADO
                || contrato.getStatus() == StatusContrato.CANCELADO) {
            throw new ContratoEstadoInvalidoException("estado anterior a APROVADO/CANCELADO",
                contrato.getStatus().name());
        }
        contrato.setStatus(StatusContrato.CANCELADO);
        return contratoRepository.save(contrato);
    }

    public Contrato vincularAgendamento(UUID contratoId, UUID agendamentoId) {
        var contrato = buscar(contratoId);
        contrato.setAgendamentoId(agendamentoId);
        return contratoRepository.save(contrato);
    }

    public static String sha256(String valor) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(
                valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}