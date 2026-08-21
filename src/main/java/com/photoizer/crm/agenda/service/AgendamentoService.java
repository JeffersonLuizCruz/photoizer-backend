package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.event.AgendamentoConfirmadoEvent;
import com.photoizer.crm.agenda.event.AgendamentoCriadoEvent;
import com.photoizer.crm.agenda.exception.AgendamentoNaoEncontradoException;
import com.photoizer.crm.agenda.exception.AgendamentoNoPassadoException;
import com.photoizer.crm.agenda.exception.EditorNaoEncontradoException;
import com.photoizer.crm.agenda.exception.FotografoNaoEncontradoException;
import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.shared.model.TipoRepasse;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.pacote.exception.PacoteInativoException;
import com.photoizer.crm.pacote.exception.PacoteNaoEncontradoException;
import com.photoizer.crm.pacote.model.Pacote;
import com.photoizer.crm.pacote.repository.PacoteRepository;
import com.photoizer.crm.cliente.exception.ClienteNaoEncontradoException;
import com.photoizer.crm.cliente.model.Cliente;
import com.photoizer.crm.cliente.model.OrigemCliente;
import com.photoizer.crm.cliente.repository.ClienteRepository;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.contrato.event.ContratoAprovadoEvent;
import com.photoizer.crm.shared.storage.FileStorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.photoizer.crm.agenda.api.AtualizarAgendamentoRequest;
import com.photoizer.crm.agenda.api.AgendamentoMapper;
import com.photoizer.crm.agenda.api.AgendamentoResponse;
import com.photoizer.crm.agenda.api.AgendamentoClienteResponse;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

@Service
@Transactional
public class AgendamentoService {

    private final ClienteRepository clienteRepository;
    private final PacoteRepository pacoteRepository;
    private final UserRepository userRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;
    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final ConfiguracaoService configuracaoService;
    private final AgendamentoFotografoRepository agendamentoFotografoRepository;
    private final DisponibilidadeService disponibilidadeService;
    private final PartilhaService partilhaService;
    private final AgendamentoValoresCalculator agendamentoValoresCalculator;
    private final AgendamentoMapper agendamentoMapper;

    public AgendamentoService(ClienteRepository clienteRepository,
                              PacoteRepository pacoteRepository,
                               UserRepository userRepository,
                              AgendamentoRepository agendamentoRepository,
                              FileStorageService fileStorageService,
                              ApplicationEventPublisher eventPublisher,
                              FotoEnsaioRepository fotoEnsaioRepository,
                              ConfiguracaoService configuracaoService,
                              AgendamentoFotografoRepository agendamentoFotografoRepository,
                              DisponibilidadeService disponibilidadeService,
                              PartilhaService partilhaService,
                              AgendamentoValoresCalculator agendamentoValoresCalculator,
                              AgendamentoMapper agendamentoMapper) {
        this.clienteRepository = clienteRepository;
        this.pacoteRepository = pacoteRepository;
        this.userRepository = userRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.fileStorageService = fileStorageService;
        this.eventPublisher = eventPublisher;
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.configuracaoService = configuracaoService;
        this.agendamentoFotografoRepository = agendamentoFotografoRepository;
        this.disponibilidadeService = disponibilidadeService;
        this.partilhaService = partilhaService;
        this.agendamentoValoresCalculator = agendamentoValoresCalculator;
        this.agendamentoMapper = agendamentoMapper;
    }

    public Agendamento criarAgendamento(CriarAgendamentoCommand command) {
        var cliente = resolverCliente(command);

        var pacote = pacoteRepository.findById(command.pacoteId())
            .orElseThrow(() -> new PacoteNaoEncontradoException(command.pacoteId()));

        var dataHoraEnsaio = resolverDataHora(command);

        var taxaDeslocamentoPadrao = configuracaoService.getValorDecimal("taxaDeslocamentoPadrao", BigDecimal.ZERO);
        var custoDeslocamento = command.custoDeslocamento() != null ? command.custoDeslocamento() : taxaDeslocamentoPadrao;
        var repassarDeslocamento = command.repassarDeslocamento() != null ? command.repassarDeslocamento() : true;
        var taxaDeslocamento = repassarDeslocamento ? custoDeslocamento : BigDecimal.ZERO;
        var autorizaUsoImagem = command.autorizaUsoImagem() != null ? command.autorizaUsoImagem() : false;

        var percentualEntrada = configuracaoService.getValorDecimal("percentualEntrada", new BigDecimal("30.00"));
        var valores = agendamentoValoresCalculator.calcularValoresNovo(
            pacote.getValorBase(), taxaDeslocamento, percentualEntrada);

        var urlComprovante = fileStorageService.salvar(command.comprovanteEntrada());

        return criarAgendamentoBase(new DadosNovoAgendamento(
            pacote,
            command.editorId(),
            cliente,
            dataHoraEnsaio,
            command.duracaoMinutos(),
            command.localEnsaio(),
            command.enderecoCompleto(),
            custoDeslocamento,
            repassarDeslocamento,
            percentualEntrada,
            valores,
            urlComprovante,
            autorizaUsoImagem,
            command.clausulasPersonalizadas(),
            command.observacoes(),
            command.fotografos(),
            command.indicadorId(),
            command.indicadorNome(),
            command.indicadorTelefone(),
            pacote.getValorBase()
        ));
    }

    @Transactional(readOnly = true)
    public List<Agendamento> listarTodos(UUID editorId, UUID fotografoId,
                                         StatusAgendamento status,
                                         LocalDateTime dataInicio, LocalDateTime dataFim, String search) {
        Specification<Agendamento> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();

            if (editorId != null) {
                predicates.add(cb.equal(root.get("editor").get("id"), editorId));
            }
            if (fotografoId != null) {
                Subquery<Long> subquery = query.subquery(Long.class);
                var subRoot = subquery.from(com.photoizer.crm.agenda.model.AgendamentoFotografo.class);
                subquery.select(cb.literal(1L));
                subquery.where(cb.and(
                    cb.equal(subRoot.get("agendamento").get("id"), root.get("id")),
                    cb.equal(subRoot.get("fotografo").get("id"), fotografoId)
                ));
                predicates.add(cb.exists(subquery));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (dataInicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataHoraEnsaio"), dataInicio));
            }
            if (dataFim != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataHoraEnsaio"), dataFim));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("cliente").get("nome")), "%" + search.toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return agendamentoRepository.findAll(spec);
    }

    @Transactional(readOnly = true)
    public Agendamento buscarPorId(UUID id) {
        return agendamentoRepository.findById(id)
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(id));
    }

    @Transactional(readOnly = true)
    public List<Agendamento> listarPorClienteId(UUID clienteId) {
        return agendamentoRepository.findByClienteId(clienteId);
    }

    @Transactional(readOnly = true)
    public List<AgendamentoClienteResponse> listarAgendamentosCliente(UUID clienteId) {
        return agendamentoRepository.findByClienteId(clienteId).stream()
            .map(a -> {
                var totalPublicadas = fotoEnsaioRepository.countByAgendamentoIdAndStatus(
                    a.getId(), StatusFoto.PUBLICADA);
                var selecionadasPacote = fotoEnsaioRepository.countSelecionadasPacoteByAgendamentoId(
                    a.getId());
                var pagas = fotoEnsaioRepository.countPagasByAgendamentoId(a.getId());
                return AgendamentoClienteResponse.of(a, totalPublicadas, selecionadasPacote, pagas);
            })
            .toList();
    }

    public AgendamentoResponse atualizar(UUID id, AtualizarAgendamentoRequest request) {
        var agendamento = buscarPorId(id);

        var pacote = pacoteRepository.findById(request.pacoteId())
            .orElseThrow(() -> new PacoteNaoEncontradoException(request.pacoteId()));
        if (!pacote.getAtivo()) {
            throw new PacoteInativoException(pacote.getId());
        }

        var editor = request.editorId() != null
            ? userRepository.findById(request.editorId())
                .orElseThrow(() -> new EditorNaoEncontradoException(request.editorId()))
            : null;

        if (request.dataHoraEnsaio().isBefore(LocalDateTime.now())) {
            throw new AgendamentoNoPassadoException();
        }

        var duracao = agendamento.getDuracaoMinutos();
        disponibilidadeService.validarConflitoAgenda(pacote, request.dataHoraEnsaio(), duracao, request.localEnsaio(), agendamento.getId());

        var taxaDeslocamentoPadrao = configuracaoService.getValorDecimal("taxaDeslocamentoPadrao", BigDecimal.ZERO);
        var custoDeslocamento = request.custoDeslocamento() != null ? request.custoDeslocamento() : taxaDeslocamentoPadrao;
        var repassarDeslocamento = request.repassarDeslocamento() != null ? request.repassarDeslocamento() : true;
        var taxaDeslocamento = repassarDeslocamento ? custoDeslocamento : BigDecimal.ZERO;

        var percentualEntrada = configuracaoService.getValorDecimal("percentualEntrada", new BigDecimal("30.00"));
        var valores = agendamentoValoresCalculator.calcularValoresAtualizacao(
            pacote.getValorBase(), taxaDeslocamento, percentualEntrada,
            agendamento.getValorEntradaPago(), agendamento.getValorExtras());

        agendamento.setPacote(pacote);
        agendamento.setEditor(editor);
        agendamento.setDataHoraEnsaio(request.dataHoraEnsaio());
        agendamento.setLocalEnsaio(request.localEnsaio());
        agendamento.setEnderecoCompleto(request.enderecoCompleto());
        agendamento.setTaxaDeslocamento(taxaDeslocamento);
        agendamento.setCustoDeslocamento(custoDeslocamento);
        agendamento.setRepassarDeslocamento(repassarDeslocamento);
        agendamento.setAutorizaUsoImagem(request.autorizaUsoImagem() != null ? request.autorizaUsoImagem() : agendamento.getAutorizaUsoImagem());
        agendamento.setObservacoes(request.observacoes());

        agendamento.setValorTotal(valores.valorTotal());
        agendamento.setValorEntradaExigido(valores.valorEntradaExigido());
        agendamento.setPercentualEntrada(percentualEntrada);
        agendamento.setValorRestante(valores.valorRestante());
        agendamento.setValorTotalFinal(valores.valorTotalFinal());

        agendamento = agendamentoRepository.save(agendamento);
        sincronizarFotografosNoAgendamento(agendamento, request.fotografos());
        partilhaService.calcularPartilhaFotografo(agendamento);
        var links = agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(agendamento.getId());
        return agendamentoMapper.toResponse(agendamento, links, null, null, null);
    }

    public Agendamento criarAgendamentoDeContrato(ContratoAprovadoEvent event) {
        var pacote = pacoteRepository.findById(event.pacoteId())
            .orElseThrow(() -> new PacoteNaoEncontradoException(event.pacoteId()));

        var cliente = resolverCliente(
            event.clienteId(), event.nome(), event.telefone(), event.email(),
            event.cpf(), event.cidade(), event.estado(), null);

        var custoDeslocamento = event.custoDeslocamento() != null
            ? event.custoDeslocamento()
            : BigDecimal.ZERO;
        var repassarDeslocamento = event.repassarDeslocamento() != null
            ? event.repassarDeslocamento()
            : true;
        var taxaDeslocamento = repassarDeslocamento ? custoDeslocamento : BigDecimal.ZERO;

        var percentualEntrada = event.percentualEntrada() != null
            ? event.percentualEntrada()
            : configuracaoService.getValorDecimal("percentualEntrada", new BigDecimal("30.00"));

        var valores = new AgendamentoValoresCalculator.ValoresAgendamento(
            event.valorTotal(),
            event.valorEntradaExigido(),
            event.valorEntradaExigido(),
            event.valorTotal().subtract(event.valorEntradaExigido()),
            BigDecimal.ZERO,
            event.valorTotal(),
            taxaDeslocamento);

        var urlComprovante = event.urlComprovanteEntrada();

        var fotografos = event.fotografos().stream()
            .map(f -> new CriarAgendamentoCommand.FotografoRepasse(
                f.fotografoId(), f.valorRepassar(), f.tipoValor(), f.percentual()))
            .toList();

        return criarAgendamentoBase(new DadosNovoAgendamento(
            pacote,
            event.editorId(),
            cliente,
            event.dataHoraEnsaio(),
            event.duracaoMinutos(),
            event.localEnsaio(),
            event.enderecoCompleto(),
            custoDeslocamento,
            repassarDeslocamento,
            percentualEntrada,
            valores,
            urlComprovante,
            event.autorizaUsoImagem() != null ? event.autorizaUsoImagem() : false,
            null,
            event.observacoes(),
            fotografos,
            event.indicadorId(),
            event.indicadorNome(),
            event.indicadorTelefone(),
            event.valorBasePacote()
        ));
    }

    /**
     * Padrão de projeto: TEMPLATE METHOD (refactor Fase 2 — agenda).
     *
     * Unifica o fluxo de criação de agendamento que antes vivia duplicado em
     * {@link #criarAgendamento(CriarAgendamentoCommand)} e
     * {@link #criarAgendamentoDeContrato(ContratoAprovadoEvent)} (~85% de código repetido).
     *
     * Melhorias trazidas:
     * - Ponto único de construção/persistência, vínculo de fotógrafos, cálculo de partilha e
     *   publicação dos Application Events (não há mais 2 caminhos de evento que podiam divergir);
     * - Validações comuns (pacote ativo, editor, data no passado e conflito de agenda) centralizadas;
     * - Valores monetários passam SEMPRE pelo {@link AgendamentoValoresCalculator} (ou via
     *   {@link ValoresAgendamento}) — elimina o cálculo manual que o fluxo de contrato possuía.
     *
     * A variação entre os fluxos (origem do cliente, taxa de deslocamento e valores) é resolvida
     * pelos chamadores e entregue de forma tipada por {@link DadosNovoAgendamento}.
     */
    private Agendamento criarAgendamentoBase(DadosNovoAgendamento dados) {
        var pacote = dados.pacote();

        if (!pacote.getAtivo()) {
            throw new PacoteInativoException(pacote.getId());
        }

        var editor = (dados.editorId() != null)
            ? userRepository.findById(dados.editorId())
                .orElseThrow(() -> new EditorNaoEncontradoException(dados.editorId()))
            : null;

        if (dados.dataHoraEnsaio().isBefore(LocalDateTime.now())) {
            throw new AgendamentoNoPassadoException();
        }

        var duracao = dados.duracaoMinutos() != null ? dados.duracaoMinutos() : 60;
        disponibilidadeService.validarConflitoAgenda(pacote, dados.dataHoraEnsaio(), duracao, dados.localEnsaio());

        var agendamento = Agendamento.builder()
            .cliente(dados.cliente())
            .pacote(pacote)
            .editor(editor)
            .dataHoraEnsaio(dados.dataHoraEnsaio())
            .duracaoMinutos(duracao)
            .localEnsaio(dados.localEnsaio())
            .enderecoCompleto(dados.enderecoCompleto())
            .valorTotal(dados.valores().valorTotal())
            .valorEntradaExigido(dados.valores().valorEntradaExigido())
            .valorEntradaPago(dados.valores().valorEntradaPago())
            .valorRestante(dados.valores().valorRestante())
            .valorExtras(dados.valores().valorExtras())
            .taxaDeslocamento(dados.valores().taxaDeslocamento())
            .custoDeslocamento(dados.custoDeslocamento())
            .repassarDeslocamento(dados.repassarDeslocamento())
            .valorTotalFinal(dados.valores().valorTotalFinal())
            .percentualEntrada(dados.percentualEntrada())
            .status(StatusAgendamento.CONFIRMADO)
            .dataConfirmacao(LocalDateTime.now())
            .urlComprovanteEntrada(dados.urlComprovanteEntrada())
            .autorizaUsoImagem(dados.autorizaUsoImagem())
            .clausulasPersonalizadas(dados.clausulasPersonalizadas())
            .contratoGerado(false)
            .ensaioDestaque(false)
            .observacoes(dados.observacoes())
            .tokenGaleria(UUID.randomUUID())
            .tokenExpiracao(LocalDateTime.now().plusDays(15))
            .build();

        agendamento = agendamentoRepository.save(agendamento);
        criarFotografosNoAgendamento(agendamento, dados.fotografos());
        partilhaService.calcularPartilhaFotografo(agendamento);

        eventPublisher.publishEvent(new AgendamentoCriadoEvent(
            agendamento.getId(),
            agendamento.getCliente().getId(),
            agendamento.getPacote().getId(),
            agendamento.getDataHoraEnsaio(),
            dados.indicadorId(),
            dados.indicadorNome(),
            dados.indicadorTelefone(),
            null,
            dados.valorBasePacote()
        ));

        eventPublisher.publishEvent(new AgendamentoConfirmadoEvent(
            agendamento.getId(),
            agendamento.getCliente().getId()
        ));

        return agendamento;
    }

    /** Dados tipados para o fluxo comum de criação (via Template Method {@link #criarAgendamentoBase}). */
    private record DadosNovoAgendamento(
        Pacote pacote,
        UUID editorId,
        Cliente cliente,
        LocalDateTime dataHoraEnsaio,
        Integer duracaoMinutos,
        String localEnsaio,
        String enderecoCompleto,
        BigDecimal custoDeslocamento,
        boolean repassarDeslocamento,
        BigDecimal percentualEntrada,
        AgendamentoValoresCalculator.ValoresAgendamento valores,
        String urlComprovanteEntrada,
        boolean autorizaUsoImagem,
        String clausulasPersonalizadas,
        String observacoes,
        List<CriarAgendamentoCommand.FotografoRepasse> fotografos,
        UUID indicadorId,
        String indicadorNome,
        String indicadorTelefone,
        BigDecimal valorBasePacote
    ) {
    }

    private void criarFotografosNoAgendamento(Agendamento agendamento, List<CriarAgendamentoCommand.FotografoRepasse> fotografos) {
        if (fotografos == null) return;
        for (var f : fotografos) {
            var fotografo = userRepository.findById(f.fotografoId())
                .orElseThrow(() -> new FotografoNaoEncontradoException(f.fotografoId()));
            var tipo = f.tipoValor() != null ? f.tipoValor() : TipoRepasse.FIXO;
            var link = AgendamentoFotografo.builder()
                .agendamento(agendamento)
                .fotografo(fotografo)
                .tipoValor(tipo)
                .percentual(tipo == TipoRepasse.PERCENTUAL ? f.percentual() : null)
                .papelParceiro(fotografo.getPapel())
                .valorRepassar(agendamentoValoresCalculator.valorRepasseEfetivo(
                    agendamento.getValorTotal(), tipo, f.valorRepassar(), f.percentual()))
                .status(RepasseStatus.PENDENTE)
                .build();
            agendamentoFotografoRepository.save(link);
        }
    }

    private void sincronizarFotografosNoAgendamento(Agendamento agendamento, List<com.photoizer.crm.agenda.api.AtualizarAgendamentoRequest.FotografoRepasse> fotografos) {
        if (fotografos == null) return;
        var existentes = agendamentoFotografoRepository.findByAgendamentoId(agendamento.getId());

        var novosIds = fotografos.stream()
            .map(com.photoizer.crm.agenda.api.AtualizarAgendamentoRequest.FotografoRepasse::fotografoId)
            .toList();

        for (var existente : existentes) {
            if (!novosIds.contains(existente.getFotografo().getId())) {
                agendamentoFotografoRepository.delete(existente);
            }
        }

        for (var f : fotografos) {
            var match = existentes.stream()
                .filter(e -> e.getFotografo().getId().equals(f.fotografoId()))
                .findFirst();
            if (match.isPresent()) {
                var link = match.get();
                var tipo = f.tipoValor() != null ? f.tipoValor() : TipoRepasse.FIXO;
                link.atualizarRepasse(
                    tipo,
                    tipo == TipoRepasse.PERCENTUAL ? f.percentual() : null,
                    agendamentoValoresCalculator.valorRepasseEfetivo(
                        agendamento.getValorTotal(), tipo, f.valorRepassar(), f.percentual()));
                agendamentoFotografoRepository.save(link);
            } else {
                var fotografo = userRepository.findById(f.fotografoId())
                    .orElseThrow(() -> new FotografoNaoEncontradoException(f.fotografoId()));
                var tipo = f.tipoValor() != null ? f.tipoValor() : TipoRepasse.FIXO;
                var link = AgendamentoFotografo.builder()
                    .agendamento(agendamento)
                    .fotografo(fotografo)
                    .tipoValor(tipo)
                    .percentual(tipo == TipoRepasse.PERCENTUAL ? f.percentual() : null)
                    .papelParceiro(fotografo.getPapel())
                    .valorRepassar(agendamentoValoresCalculator.valorRepasseEfetivo(
                        agendamento.getValorTotal(), tipo, f.valorRepassar(), f.percentual()))
                    .status(RepasseStatus.PENDENTE)
                    .build();
                agendamentoFotografoRepository.save(link);
            }
        }
    }

    private Cliente resolverCliente(CriarAgendamentoCommand command) {
        return resolverCliente(
            command.clienteId(), command.nome(), command.telefone(), command.email(),
            command.cpf(), command.cidade(), command.estado(), command.origem());
    }

    private Cliente resolverCliente(UUID clienteId, String nome, String telefone, String email,
                                    String cpf, String cidade, String estado, String origem) {
        if (clienteId != null) {
            return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ClienteNaoEncontradoException(clienteId));
        }

        if (telefone != null && !telefone.isBlank()) {
            var clienteExistente = clienteRepository.findByTelefone(telefone);
            if (clienteExistente.isPresent()) {
                return clienteExistente.get();
            }
        }

        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome do cliente é obrigatório quando não informado um clienteId");
        }
        if (telefone == null || telefone.isBlank()) {
            throw new IllegalArgumentException("Telefone do cliente é obrigatório quando não informado um clienteId");
        }

        OrigemCliente origemCliente = OrigemCliente.OUTROS;
        if (origem != null && !origem.isBlank()) {
            try {
                origemCliente = OrigemCliente.valueOf(origem);
            } catch (IllegalArgumentException e) {
                origemCliente = OrigemCliente.OUTROS;
            }
        }

        if (cpf != null && !cpf.isBlank()) {
            var porCpf = clienteRepository.findByCpf(cpf);
            if (porCpf.isPresent()) {
                return porCpf.get();
            }
        }

        var cliente = Cliente.builder()
            .nome(nome)
            .telefone(telefone)
            .email(email)
            .cpf(cpf)
            .cidade(cidade)
            .estado(estado)
            .origem(origemCliente)
            .build();

        return clienteRepository.save(cliente);
    }

    private LocalDateTime resolverDataHora(CriarAgendamentoCommand command) {
        if (command.dataHoraEnsaio() != null) {
            return command.dataHoraEnsaio();
        }
        if (command.data() != null && command.hora() != null && !command.hora().isBlank()) {
            var time = LocalTime.parse(command.hora(), DateTimeFormatter.ofPattern("HH:mm"));
            return LocalDateTime.of(command.data(), time);
        }
        throw new IllegalArgumentException("Data e hora do ensaio são obrigatórias (dataHoraEnsaio ou data + hora)");
    }
}
