package com.photoizer.crm.contrato.service;

import com.photoizer.crm.agenda.exception.AgendamentoNoPassadoException;
import com.photoizer.crm.agenda.exception.EditorNaoEncontradoException;
import com.photoizer.crm.agenda.exception.FotografoNaoEncontradoException;
import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.contrato.api.CriarContratoRequest;
import com.photoizer.crm.contrato.api.DevolverContratoRequest;
import com.photoizer.crm.contrato.api.PublicarContratoResponse;
import com.photoizer.crm.contrato.event.ContratoAprovadoEvent;
import com.photoizer.crm.contrato.exception.ContratoNaoEncontradoException;
import com.photoizer.crm.contrato.model.Contrato;
import com.photoizer.crm.contrato.model.ContratoFotografo;
import com.photoizer.crm.contrato.model.StatusContrato;
import com.photoizer.crm.contrato.repository.ContratoRepository;
import com.photoizer.crm.pacote.exception.PacoteInativoException;
import com.photoizer.crm.pacote.exception.PacoteNaoEncontradoException;
import com.photoizer.crm.pacote.repository.PacoteRepository;
import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.shared.model.TipoRepasse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class GestaoContratoService {

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
            : configuracaoService.getValorDecimal(ConfigKey.TAXA_DESLOCAMENTO);
        var repassarDeslocamento = request.repassarDeslocamento() != null
            ? request.repassarDeslocamento()
            : true;
        var taxaDeslocamento = repassarDeslocamento ? custoDeslocamento : BigDecimal.ZERO;

        var percentualEntrada = configuracaoService.getValorDecimal(ConfigKey.PERCENTUAL_ENTRADA);
        var fatorEntrada = percentualEntrada.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        var valorTotal = pacote.getValorBase().add(taxaDeslocamento);
        var valorEntradaExigido = valorTotal.multiply(fatorEntrada).setScale(2, RoundingMode.HALF_UP);
        var valorRestante = valorTotal.subtract(valorEntradaExigido);

        var fotografos = resolverFotografos(request, valorTotal);

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
            .fotografoId(fotografos.isEmpty() ? null : fotografos.getFirst().getFotografo().getId())
            .valorRepassarFotografo(fotografos.isEmpty() ? null : fotografos.getFirst().getValorRepassar())
            .build();

        fotografos.forEach(contrato::addFotografo);
        return contratoRepository.save(contrato);
    }

    private List<ContratoFotografo> resolverFotografos(CriarContratoRequest request, BigDecimal valorTotal) {
        var links = new ArrayList<ContratoFotografo>();
        if (request.fotografos() != null && !request.fotografos().isEmpty()) {
            for (var f : request.fotografos()) {
                links.add(montarLink(f.fotografoId(), f.tipoValor(), f.valorRepassar(), f.percentual(), valorTotal));
            }
        } else if (request.fotografoId() != null) {
            links.add(montarLink(request.fotografoId(), TipoRepasse.FIXO,
                request.valorRepassarFotografo(), null, valorTotal));
        }

        var soma = links.stream()
            .map(ContratoFotografo::getValorRepassar)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (soma.compareTo(valorTotal) > 0) {
            throw new IllegalArgumentException(
                "A soma dos repasses (R$ " + soma.toPlainString() + ") excede o valor total do ensaio (R$ "
                    + valorTotal.toPlainString() + ")");
        }
        return links;
    }

    private ContratoFotografo montarLink(UUID fotografoId, TipoRepasse tipo, BigDecimal valorRepassar,
                                         BigDecimal percentual, BigDecimal base) {
        var user = userRepository.findById(fotografoId)
            .orElseThrow(() -> new FotografoNaoEncontradoException(fotografoId));
        var tipoEfetivo = tipo != null ? tipo : TipoRepasse.FIXO;
        return ContratoFotografo.builder()
            .fotografo(user)
            .tipoValor(tipoEfetivo)
            .percentual(tipoEfetivo == TipoRepasse.PERCENTUAL ? percentual : null)
            .papelParceiro(user.getPapel())
            .valorRepassar(valorRepasseEfetivo(base, tipoEfetivo, valorRepassar, percentual))
            .build();
    }

    private BigDecimal valorRepasseEfetivo(BigDecimal base, TipoRepasse tipo, BigDecimal valorRepassar, BigDecimal percentual) {
        if (tipo == TipoRepasse.PERCENTUAL) {
            var pct = percentual != null ? percentual : BigDecimal.ZERO;
            return base.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return valorRepassar != null ? valorRepassar : BigDecimal.ZERO;
    }

    public PublicarContratoResponse publicar(UUID id) {
        var contrato = buscar(id);
        var token = UUID.randomUUID().toString();
        var diasValidade = configuracaoService.getValorInteiro(ConfigKey.CONTRATO_DIAS_VALIDADE);

        // Delegate para o dominio: valida transicao E executa
        contrato.publicar(sha256(token), diasValidade);
        contratoRepository.save(contrato);

        return new PublicarContratoResponse(contrato.getId(), "/contrato/" + token);
    }

    @Transactional(readOnly = true)
    public Contrato buscar(UUID id) {
        return contratoRepository.findById(id)
            .orElseThrow(() -> new ContratoNaoEncontradoException(id));
    }

    @Transactional(readOnly = true)
    public Page<Contrato> listar(StatusContrato status, String search, int page, int size) {
        Specification<Contrato> spec = (root, q, cb) -> null;

        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }

        if (search != null && !search.isBlank()) {
            var termo = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                cb.like(cb.lower(root.get("clienteNome")), termo),
                cb.like(cb.lower(root.get("clienteTelefone")), termo),
                cb.like(cb.lower(root.get("pacoteNome")), termo)
            ));
        }

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return contratoRepository.findAll(spec, pageable);
    }

    public Contrato confirmarPagamento(UUID id) {
        var contrato = buscar(id);
        contrato.confirmarPagamento();
        return contratoRepository.save(contrato);
    }

    public Contrato aprovar(UUID id) {
        var contrato = buscar(id);
        contrato.aprovar();
        contratoRepository.save(contrato);

        var fotografos = contrato.getFotografos() == null
            ? List.<ContratoAprovadoEvent.FotografoRepasse>of()
            : contrato.getFotografos().stream()
                .map(cf -> new ContratoAprovadoEvent.FotografoRepasse(
                    cf.getFotografo().getId(),
                    cf.getValorRepassar(),
                    cf.getTipoValor() != null ? cf.getTipoValor() : TipoRepasse.FIXO,
                    cf.getPercentual()))
                .toList();

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
            contrato.getRepassarDeslocamento(),
            fotografos
        ));

        return contrato;
    }

    public Contrato devolver(UUID id, DevolverContratoRequest request) {
        var contrato = buscar(id);
        contrato.devolver(request.tipoMotivo(), request.motivo());
        contratoRepository.save(contrato);

        eventPublisher.publishEvent(new com.photoizer.crm.contrato.event.ContratoDevolvidoEvent(
            contrato.getId(), request.tipoMotivo(), request.motivo()));

        return contrato;
    }

    public Contrato cancelar(UUID id) {
        var contrato = buscar(id);
        contrato.cancelar();
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
