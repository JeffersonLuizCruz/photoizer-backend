package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.event.AgendamentoCanceladoEvent;
import com.photoizer.crm.agenda.event.AgendamentoConfirmadoEvent;
import com.photoizer.crm.agenda.event.AgendamentoCriadoEvent;
import com.photoizer.crm.agenda.event.AgendamentoRealizadoEvent;
import com.photoizer.crm.agenda.event.PagamentoFinalRegistradoEvent;
import com.photoizer.crm.agenda.exception.AgendamentoNaoEncontradoException;
import com.photoizer.crm.agenda.exception.AgendamentoNoPassadoException;
import com.photoizer.crm.agenda.exception.ConflitoDeAgendaException;
import com.photoizer.crm.agenda.exception.EditorNaoEncontradoException;
import com.photoizer.crm.agenda.exception.FotografoNaoEncontradoException;
import com.photoizer.crm.agenda.exception.EnsaioNaoFinalizadoException;
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
import com.photoizer.crm.shared.storage.FileStorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.photoizer.crm.agenda.api.AtualizarAgendamentoRequest;
import com.photoizer.crm.agenda.api.AgendamentoResponse;
import com.photoizer.crm.agenda.api.DisponibilidadeResponse;
import com.photoizer.crm.cliente.api.AgendamentoClienteResponse;
import com.photoizer.crm.despesa.service.DespesaService;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
    private final DespesaService despesaService;
    private final AgendamentoFotografoRepository agendamentoFotografoRepository;

    public AgendamentoService(ClienteRepository clienteRepository,
                              PacoteRepository pacoteRepository,
                               UserRepository userRepository,
                              AgendamentoRepository agendamentoRepository,
                              FileStorageService fileStorageService,
                              ApplicationEventPublisher eventPublisher,
                              FotoEnsaioRepository fotoEnsaioRepository,
                              ConfiguracaoService configuracaoService,
                              DespesaService despesaService,
                              AgendamentoFotografoRepository agendamentoFotografoRepository) {
        this.clienteRepository = clienteRepository;
        this.pacoteRepository = pacoteRepository;
        this.userRepository = userRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.fileStorageService = fileStorageService;
        this.eventPublisher = eventPublisher;
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.configuracaoService = configuracaoService;
        this.despesaService = despesaService;
        this.agendamentoFotografoRepository = agendamentoFotografoRepository;
    }

    public Agendamento criarAgendamento(CriarAgendamentoCommand command) {
        var cliente = resolverCliente(command);

        var pacote = pacoteRepository.findById(command.pacoteId())
            .orElseThrow(() -> new PacoteNaoEncontradoException(command.pacoteId()));

        if (!pacote.getAtivo()) {
            throw new PacoteInativoException(pacote.getId());
        }

        var editor = (command.editorId() != null)
            ? userRepository.findById(command.editorId())
                .orElseThrow(() -> new EditorNaoEncontradoException(command.editorId()))
            : null;

        var dataHoraEnsaio = resolverDataHora(command);

        if (dataHoraEnsaio.isBefore(LocalDateTime.now())) {
            throw new AgendamentoNoPassadoException();
        }

        var duracao = command.duracaoMinutos() != null ? command.duracaoMinutos() : 60;
        var taxaDeslocamentoPadrao = configuracaoService.getValorDecimal("taxaDeslocamentoPadrao", BigDecimal.ZERO);
        var custoDeslocamento = command.custoDeslocamento() != null ? command.custoDeslocamento() : taxaDeslocamentoPadrao;
        var repassarDeslocamento = command.repassarDeslocamento() != null ? command.repassarDeslocamento() : true;
        var taxaDeslocamento = repassarDeslocamento ? custoDeslocamento : BigDecimal.ZERO;
        var autorizaUsoImagem = command.autorizaUsoImagem() != null ? command.autorizaUsoImagem() : false;

        validarConflitoAgenda(pacote, dataHoraEnsaio, duracao, command.localEnsaio());

        var percentualEntrada = configuracaoService.getValorDecimal("percentualEntrada", new BigDecimal("30.00"));
        var fatorEntrada = percentualEntrada.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        var valorTotal = pacote.getValorBase().add(taxaDeslocamento);
        var valorEntradaExigido = valorTotal.multiply(fatorEntrada)
            .setScale(2, RoundingMode.HALF_UP);
        var valorEntradaPago = valorEntradaExigido;
        var valorRestante = valorTotal.subtract(valorEntradaPago);
        var valorExtras = BigDecimal.ZERO;
        var valorTotalFinal = valorTotal.add(valorExtras);

        var urlComprovante = fileStorageService.salvar(command.comprovanteEntrada());

        var agendamento = Agendamento.builder()
            .cliente(cliente)
            .pacote(pacote)
            .editor(editor)
            .dataHoraEnsaio(dataHoraEnsaio)
            .duracaoMinutos(duracao)
            .localEnsaio(command.localEnsaio())
            .enderecoCompleto(command.enderecoCompleto())
            .valorTotal(valorTotal)
            .valorEntradaExigido(valorEntradaExigido)
            .valorEntradaPago(valorEntradaPago)
            .valorRestante(valorRestante)
            .valorExtras(valorExtras)
            .taxaDeslocamento(taxaDeslocamento)
            .custoDeslocamento(custoDeslocamento)
            .repassarDeslocamento(repassarDeslocamento)
            .valorTotalFinal(valorTotalFinal)
            .percentualEntrada(percentualEntrada)
            .status(StatusAgendamento.CONFIRMADO)
            .dataConfirmacao(LocalDateTime.now())
            .urlComprovanteEntrada(urlComprovante)
            .autorizaUsoImagem(autorizaUsoImagem)
            .clausulasPersonalizadas(command.clausulasPersonalizadas())
            .contratoGerado(false)
            .ensaioDestaque(false)
            .observacoes(command.observacoes())
            .tokenGaleria(UUID.randomUUID())
            .tokenExpiracao(LocalDateTime.now().plusDays(15))
            .build();

        agendamento = agendamentoRepository.save(agendamento);
        criarFotografosNoAgendamento(agendamento, command.fotografos());
        calcularPartilhaFotografo(agendamento);

        eventPublisher.publishEvent(new AgendamentoCriadoEvent(
            agendamento.getId(),
            agendamento.getCliente().getId(),
            agendamento.getPacote().getId(),
            agendamento.getDataHoraEnsaio(),
            command.indicadorId(),
            command.indicadorNome(),
            command.indicadorTelefone(),
            null,
            pacote.getValorBase()
        ));

        eventPublisher.publishEvent(new AgendamentoConfirmadoEvent(
            agendamento.getId(),
            agendamento.getCliente().getId()
        ));

        return agendamento;
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

    public Agendamento atualizarStatus(UUID id, String novoStatus) {
        var agendamento = buscarPorId(id);
        var status = StatusAgendamento.valueOf(novoStatus);
        agendamento.setStatus(status);

        if (status == StatusAgendamento.REALIZADO) {
            agendamento.setDataRealizacao(LocalDateTime.now());
            eventPublisher.publishEvent(new AgendamentoRealizadoEvent(
                agendamento.getId(),
                agendamento.getCliente().getId()
            ));
        }

        if (status == StatusAgendamento.CANCELADO || status == StatusAgendamento.NO_SHOW) {
            eventPublisher.publishEvent(new AgendamentoCanceladoEvent(agendamento.getId()));
        }

        return agendamentoRepository.save(agendamento);
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

    public Agendamento reagendar(UUID id, LocalDate data, String hora, Integer duracaoMinutos) {
        var agendamento = buscarPorId(id);

        LocalTime time = (hora != null && !hora.isBlank())
            ? LocalTime.parse(hora, DateTimeFormatter.ofPattern("HH:mm"))
            : agendamento.getDataHoraEnsaio().toLocalTime();

        LocalDate novaData = (data != null) ? data : agendamento.getDataHoraEnsaio().toLocalDate();
        LocalDateTime novaDataHora = LocalDateTime.of(novaData, time);

        int duracao = (duracaoMinutos != null) ? duracaoMinutos : agendamento.getDuracaoMinutos();

        var pacote = agendamento.getPacote();
        validarConflitoAgenda(pacote, novaDataHora, duracao, agendamento.getLocalEnsaio());

        agendamento.setDataHoraEnsaio(novaDataHora);
        agendamento.setDuracaoMinutos(duracao);
        agendamento.setStatus(StatusAgendamento.CONFIRMADO);
        agendamento.setDataConfirmacao(LocalDateTime.now());

        agendamento = agendamentoRepository.save(agendamento);

        eventPublisher.publishEvent(new AgendamentoConfirmadoEvent(
            agendamento.getId(),
            agendamento.getCliente().getId()
        ));

        return agendamento;
    }

    public Agendamento toggleDestaque(UUID id) {
        var agendamento = buscarPorId(id);
        agendamento.setEnsaioDestaque(!agendamento.getEnsaioDestaque());
        return agendamentoRepository.save(agendamento);
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
        validarConflitoAgenda(pacote, request.dataHoraEnsaio(), duracao, request.localEnsaio(), agendamento.getId());

        var taxaDeslocamentoPadrao = configuracaoService.getValorDecimal("taxaDeslocamentoPadrao", BigDecimal.ZERO);
        var custoDeslocamento = request.custoDeslocamento() != null ? request.custoDeslocamento() : taxaDeslocamentoPadrao;
        var repassarDeslocamento = request.repassarDeslocamento() != null ? request.repassarDeslocamento() : true;
        var taxaDeslocamento = repassarDeslocamento ? custoDeslocamento : BigDecimal.ZERO;

        var percentualEntrada = configuracaoService.getValorDecimal("percentualEntrada", new BigDecimal("30.00"));
        var fatorEntrada = percentualEntrada.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        var novoValorTotal = pacote.getValorBase().add(taxaDeslocamento);
        var novoValorEntradaExigido = novoValorTotal.multiply(fatorEntrada)
            .setScale(2, RoundingMode.HALF_UP);
        var novoValorRestante = novoValorTotal.subtract(agendamento.getValorEntradaPago());
        var novoValorTotalFinal = novoValorTotal.add(agendamento.getValorExtras());

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

        agendamento.setValorTotal(novoValorTotal);
        agendamento.setValorEntradaExigido(novoValorEntradaExigido);
        agendamento.setPercentualEntrada(percentualEntrada);
        agendamento.setValorRestante(novoValorRestante);
        agendamento.setValorTotalFinal(novoValorTotalFinal);

        agendamento = agendamentoRepository.save(agendamento);
        sincronizarFotografosNoAgendamento(agendamento, request.fotografos());
        calcularPartilhaFotografo(agendamento);
        var links = agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(agendamento.getId());
        return AgendamentoResponse.of(agendamento, links, null, null, null);
    }

    public DisponibilidadeResponse verificarDisponibilidade(LocalDate data, String hora, Integer duracaoMinutos, UUID excluirAgendamentoId, Boolean bloqueiaDiaInteiro) {
        var inicioDia = data.atStartOfDay();
        var fimDia = data.atTime(23, 59, 59);
        var statusesIgnorados = List.of(StatusAgendamento.CANCELADO, StatusAgendamento.NO_SHOW);

        List<Agendamento> agendamentosNoDia;
        if (excluirAgendamentoId != null) {
            agendamentosNoDia = agendamentoRepository.findByLocalAndDataBetweenExcludingId(
                inicioDia, fimDia, statusesIgnorados, excluirAgendamentoId);
        } else {
            agendamentosNoDia = agendamentoRepository.findByDataBetween(inicioDia, fimDia, statusesIgnorados);
        }

        var conflitos = new ArrayList<DisponibilidadeResponse.Conflito>();

        if (Boolean.TRUE.equals(bloqueiaDiaInteiro)) {
            for (var existente : agendamentosNoDia) {
                conflitos.add(new DisponibilidadeResponse.Conflito(
                    existente.getId(),
                    existente.getDataHoraEnsaio().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                    existente.getCliente().getNome()
                ));
            }
        } else {
            var time = LocalTime.parse(hora, DateTimeFormatter.ofPattern("HH:mm"));
            var dataHora = LocalDateTime.of(data, time);
            var duracao = duracaoMinutos != null ? duracaoMinutos : 60;
            var novoFim = dataHora.plusMinutes(duracao);

            for (var existente : agendamentosNoDia) {
                var fimExistente = existente.getDataHoraEnsaio().plusMinutes(existente.getDuracaoMinutos());
                if (dataHora.isBefore(fimExistente) && novoFim.isAfter(existente.getDataHoraEnsaio())) {
                    conflitos.add(new DisponibilidadeResponse.Conflito(
                        existente.getId(),
                        existente.getDataHoraEnsaio().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                        existente.getCliente().getNome()
                    ));
                }
            }
        }

        return new DisponibilidadeResponse(conflitos.isEmpty(), conflitos);
    }

    public Agendamento registrarPagamentoFinal(UUID id, org.springframework.web.multipart.MultipartFile comprovante) {
        var agendamento = buscarPorId(id);

        if (agendamento.getStatus() != StatusAgendamento.REALIZADO
            && agendamento.getStatus() != StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL) {
            throw new EnsaioNaoFinalizadoException(
                "O agendamento precisa estar como REALIZADO ou AGUARDANDO_PAGAMENTO_FINAL para registrar o pagamento final. Status atual: " + agendamento.getStatus()
            );
        }

        if (comprovante == null || comprovante.isEmpty()) {
            throw new IllegalArgumentException("Comprovante de pagamento é obrigatório para finalizar o ensaio");
        }

        var url = fileStorageService.salvar(comprovante);
        agendamento.setUrlComprovanteFinal(url);

        agendamento.setValorRestante(BigDecimal.ZERO);
        agendamento.setValorEntradaPago(agendamento.getValorTotalFinal());
        agendamento.setStatus(StatusAgendamento.EM_EDICAO);
        agendamento.setDataEnvioSelecao(LocalDateTime.now());

        agendamento = agendamentoRepository.save(agendamento);

        eventPublisher.publishEvent(new PagamentoFinalRegistradoEvent(
            agendamento.getId(),
            agendamento.getValorTotalFinal()
        ));

        return agendamento;
    }

    public Agendamento criarAgendamentoDeContrato(com.photoizer.crm.contrato.event.ContratoAprovadoEvent event) {
        var pacote = pacoteRepository.findById(event.pacoteId())
            .orElseThrow(() -> new PacoteNaoEncontradoException(event.pacoteId()));

        if (!pacote.getAtivo()) {
            throw new PacoteInativoException(pacote.getId());
        }

        var editor = event.editorId() != null
            ? userRepository.findById(event.editorId())
                .orElseThrow(() -> new EditorNaoEncontradoException(event.editorId()))
            : null;

        if (event.dataHoraEnsaio().isBefore(LocalDateTime.now())) {
            throw new AgendamentoNoPassadoException();
        }

        var duracao = event.duracaoMinutos() != null ? event.duracaoMinutos() : 60;
        validarConflitoAgenda(pacote, event.dataHoraEnsaio(), duracao, event.localEnsaio());

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
        var valorTotal = event.valorTotal();
        var valorEntradaExigido = event.valorEntradaExigido();
        var valorEntradaPago = valorEntradaExigido;
        var valorRestante = valorTotal.subtract(valorEntradaPago);
        var valorExtras = BigDecimal.ZERO;
        var valorTotalFinal = valorTotal.add(valorExtras);

        var agendamento = Agendamento.builder()
            .cliente(cliente)
            .pacote(pacote)
            .editor(editor)
            .dataHoraEnsaio(event.dataHoraEnsaio())
            .duracaoMinutos(duracao)
            .localEnsaio(event.localEnsaio())
            .enderecoCompleto(event.enderecoCompleto())
            .valorTotal(valorTotal)
            .valorEntradaExigido(valorEntradaExigido)
            .valorEntradaPago(valorEntradaPago)
            .valorRestante(valorRestante)
            .valorExtras(valorExtras)
            .taxaDeslocamento(taxaDeslocamento)
            .custoDeslocamento(custoDeslocamento)
            .repassarDeslocamento(repassarDeslocamento)
            .valorTotalFinal(valorTotalFinal)
            .percentualEntrada(percentualEntrada)
            .status(StatusAgendamento.CONFIRMADO)
            .dataConfirmacao(LocalDateTime.now())
            .urlComprovanteEntrada(event.urlComprovanteEntrada())
            .autorizaUsoImagem(event.autorizaUsoImagem() != null ? event.autorizaUsoImagem() : false)
            .contratoGerado(false)
            .ensaioDestaque(false)
            .observacoes(event.observacoes())
            .tokenGaleria(UUID.randomUUID())
            .tokenExpiracao(LocalDateTime.now().plusDays(15))
            .build();

        agendamento = agendamentoRepository.save(agendamento);
        criarFotografosNoAgendamento(agendamento, event.fotografos().stream()
            .map(f -> new CriarAgendamentoCommand.FotografoRepasse(
                f.fotografoId(), f.valorRepassar(), f.tipoValor(), f.percentual()))
            .toList());
        calcularPartilhaFotografo(agendamento);

        eventPublisher.publishEvent(new AgendamentoCriadoEvent(
            agendamento.getId(),
            agendamento.getCliente().getId(),
            agendamento.getPacote().getId(),
            agendamento.getDataHoraEnsaio(),
            event.indicadorId(),
            event.indicadorNome(),
            event.indicadorTelefone(),
            null,
            event.valorBasePacote()
        ));

        eventPublisher.publishEvent(new AgendamentoConfirmadoEvent(
            agendamento.getId(),
            agendamento.getCliente().getId()
        ));

        return agendamento;
    }

    public void calcularPartilhaFotografo(Agendamento agendamento) {
        var links = agendamentoFotografoRepository.findByAgendamentoId(agendamento.getId());
        if (links.isEmpty()) {
            agendamento.setValorPartilhaGlobal(null);
            agendamento.setValorLucroCrm(null);
            return;
        }

        var custosTotais = despesaService.somarCustosTodosFotografos(agendamento.getId());
        var partilhaGlobal = agendamento.getValorTotalFinal().subtract(custosTotais);
        var somaRepasses = agendamentoFotografoRepository.findByAgendamentoId(agendamento.getId()).stream()
            .filter(l -> l.getStatus() != RepasseStatus.CANCELADO)
            .map(AgendamentoFotografo::getValorRepassar)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (somaRepasses.compareTo(partilhaGlobal) > 0) {
            throw new IllegalArgumentException(
                "A soma dos repasses (R$ " + somaRepasses.toPlainString() + ") excede a partilha do ensaio (R$ "
                    + partilhaGlobal.toPlainString() + ")");
        }
        var lucro = partilhaGlobal.subtract(somaRepasses);

        agendamento.setValorPartilhaGlobal(partilhaGlobal);
        agendamento.setValorLucroCrm(lucro);
        agendamentoRepository.save(agendamento);
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
                .valorRepassar(valorRepasseEfetivo(agendamento, tipo, f.valorRepassar(), f.percentual()))
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
                link.setTipoValor(tipo);
                link.setPercentual(tipo == TipoRepasse.PERCENTUAL ? f.percentual() : null);
                link.setValorRepassar(valorRepasseEfetivo(agendamento, tipo, f.valorRepassar(), f.percentual()));
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
                    .valorRepassar(valorRepasseEfetivo(agendamento, tipo, f.valorRepassar(), f.percentual()))
                    .status(RepasseStatus.PENDENTE)
                    .build();
                agendamentoFotografoRepository.save(link);
            }
        }
    }

    private BigDecimal valorRepasseEfetivo(Agendamento agendamento, TipoRepasse tipo, BigDecimal valorRepassar, BigDecimal percentual) {
        if (tipo == TipoRepasse.PERCENTUAL) {
            var base = agendamento.getValorTotal() != null ? agendamento.getValorTotal() : BigDecimal.ZERO;
            var pct = percentual != null ? percentual : BigDecimal.ZERO;
            return base.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return valorRepassar != null ? valorRepassar : BigDecimal.ZERO;
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

    private void validarConflitoAgenda(Pacote pacote, LocalDateTime dataHora, int duracao, String local, UUID excluirId) {
        if (pacote.getBloqueiaDiaInteiro()) {
            var inicioDia = dataHora.toLocalDate().atStartOfDay();
            var fimDia = dataHora.toLocalDate().atTime(23, 59, 59);
            var conflito = agendamentoRepository.existsByDataHoraEnsaioBetweenAndStatusNotAndIdNot(
                inicioDia, fimDia, StatusAgendamento.CANCELADO, excluirId);
            if (conflito) {
                throw new ConflitoDeAgendaException(
                    "Já existe um agendamento nesta data. O pacote selecionado bloqueia o dia inteiro.");
            }
            return;
        }

        var inicioDia = dataHora.toLocalDate().atStartOfDay();
        var fimDia = dataHora.toLocalDate().atTime(23, 59, 59);
        var statusesIgnorados = List.of(StatusAgendamento.CANCELADO, StatusAgendamento.NO_SHOW);
        var agendamentosNoDia = agendamentoRepository.findByLocalAndDataBetweenExcludingId(
            inicioDia, fimDia, statusesIgnorados, excluirId);

        var novoFim = dataHora.plusMinutes(duracao);

        for (var existente : agendamentosNoDia) {
            var fimExistente = existente.getDataHoraEnsaio()
                .plusMinutes(existente.getDuracaoMinutos());
            if (dataHora.isBefore(fimExistente) && novoFim.isAfter(existente.getDataHoraEnsaio())) {
                throw new ConflitoDeAgendaException(
                    "Já existe um agendamento neste horário e local: "
                    + existente.getDataHoraEnsaio() + " às " + fimExistente);
            }
        }
    }

    private void validarConflitoAgenda(Pacote pacote, LocalDateTime dataHora, int duracao, String local) {
        if (pacote.getBloqueiaDiaInteiro()) {
            var inicioDia = dataHora.toLocalDate().atStartOfDay();
            var fimDia = dataHora.toLocalDate().atTime(23, 59, 59);
            var conflito = agendamentoRepository.existsByDataHoraEnsaioBetweenAndStatusNot(
                inicioDia, fimDia, StatusAgendamento.CANCELADO);
            if (conflito) {
                throw new ConflitoDeAgendaException(
                    "Já existe um agendamento nesta data. O pacote selecionado bloqueia o dia inteiro.");
            }
            return;
        }

        var inicioDia = dataHora.toLocalDate().atStartOfDay();
        var fimDia = dataHora.toLocalDate().atTime(23, 59, 59);
        var statusesIgnorados = List.of(StatusAgendamento.CANCELADO, StatusAgendamento.NO_SHOW);
        var agendamentosNoDia = agendamentoRepository.findByLocalAndDataBetween(
            local, inicioDia, fimDia, statusesIgnorados);

        var novoFim = dataHora.plusMinutes(duracao);

        for (var existente : agendamentosNoDia) {
            var fimExistente = existente.getDataHoraEnsaio()
                .plusMinutes(existente.getDuracaoMinutos());
            if (dataHora.isBefore(fimExistente) && novoFim.isAfter(existente.getDataHoraEnsaio())) {
                throw new ConflitoDeAgendaException(
                    "Já existe um agendamento neste horário e local: "
                    + existente.getDataHoraEnsaio() + " às " + fimExistente);
            }
        }
    }
}
