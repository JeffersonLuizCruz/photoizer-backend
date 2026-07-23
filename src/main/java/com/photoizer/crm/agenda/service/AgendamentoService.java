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
import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.agenda.repository.UsuarioRepository;
import com.photoizer.crm.pacote.exception.PacoteInativoException;
import com.photoizer.crm.pacote.exception.PacoteNaoEncontradoException;
import com.photoizer.crm.pacote.model.Pacote;
import com.photoizer.crm.pacote.repository.PacoteRepository;
import com.photoizer.crm.cliente.exception.ClienteNaoEncontradoException;
import com.photoizer.crm.cliente.model.Cliente;
import com.photoizer.crm.cliente.model.OrigemCliente;
import com.photoizer.crm.cliente.repository.ClienteRepository;
import com.photoizer.crm.shared.storage.FileStorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.photoizer.crm.agenda.api.AtualizarAgendamentoRequest;
import com.photoizer.crm.agenda.api.AgendamentoResponse;
import com.photoizer.crm.agenda.api.DisponibilidadeResponse;
import com.photoizer.crm.cliente.api.AgendamentoClienteResponse;
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
import org.springframework.data.jpa.domain.Specification;

@Service
@Transactional
public class AgendamentoService {

    private final ClienteRepository clienteRepository;
    private final PacoteRepository pacoteRepository;
    private final UsuarioRepository usuarioRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;
    private final FotoEnsaioRepository fotoEnsaioRepository;

    public AgendamentoService(ClienteRepository clienteRepository,
                              PacoteRepository pacoteRepository,
                              UsuarioRepository usuarioRepository,
                              AgendamentoRepository agendamentoRepository,
                              FileStorageService fileStorageService,
                              ApplicationEventPublisher eventPublisher,
                              FotoEnsaioRepository fotoEnsaioRepository) {
        this.clienteRepository = clienteRepository;
        this.pacoteRepository = pacoteRepository;
        this.usuarioRepository = usuarioRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.fileStorageService = fileStorageService;
        this.eventPublisher = eventPublisher;
        this.fotoEnsaioRepository = fotoEnsaioRepository;
    }

    public Agendamento criarAgendamento(CriarAgendamentoCommand command) {
        var cliente = resolverCliente(command);

        var pacote = pacoteRepository.findById(command.pacoteId())
            .orElseThrow(() -> new PacoteNaoEncontradoException(command.pacoteId()));

        if (!pacote.getAtivo()) {
            throw new PacoteInativoException(pacote.getId());
        }

        var editor = (command.editorId() != null)
            ? usuarioRepository.findById(command.editorId())
                .orElseThrow(() -> new EditorNaoEncontradoException(command.editorId()))
            : null;

        var dataHoraEnsaio = resolverDataHora(command);

        if (dataHoraEnsaio.isBefore(LocalDateTime.now())) {
            throw new AgendamentoNoPassadoException();
        }

        var duracao = command.duracaoMinutos() != null ? command.duracaoMinutos() : 60;
        var taxaDeslocamento = command.taxaDeslocamento() != null ? command.taxaDeslocamento() : BigDecimal.ZERO;
        var autorizaUsoImagem = command.autorizaUsoImagem() != null ? command.autorizaUsoImagem() : false;

        validarConflitoAgenda(pacote, dataHoraEnsaio, duracao, command.localEnsaio());

        var valorTotal = pacote.getValorBase().add(taxaDeslocamento);
        var valorEntradaExigido = pacote.getValorBase().multiply(new BigDecimal("0.30"))
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
            .valorTotalFinal(valorTotalFinal)
            .status(StatusAgendamento.CONFIRMADO)
            .dataConfirmacao(LocalDateTime.now())
            .urlComprovanteEntrada(urlComprovante)
            .autorizaUsoImagem(autorizaUsoImagem)
            .clausulasPersonalizadas(command.clausulasPersonalizadas())
            .contratoGerado(false)
            .ensaioDestaque(false)
            .observacoes(command.observacoes())
            .tokenGaleria(UUID.randomUUID())
            .build();

        agendamento = agendamentoRepository.save(agendamento);

        eventPublisher.publishEvent(new AgendamentoCriadoEvent(
            agendamento.getId(),
            agendamento.getCliente().getId(),
            agendamento.getPacote().getId(),
            agendamento.getDataHoraEnsaio(),
            command.indicadorNome(),
            command.indicadorTelefone(),
            null,
            agendamento.getValorTotal()
        ));

        eventPublisher.publishEvent(new AgendamentoConfirmadoEvent(
            agendamento.getId(),
            agendamento.getCliente().getId()
        ));

        return agendamento;
    }

    @Transactional(readOnly = true)
    public List<Agendamento> listarTodos(UUID editorId, StatusAgendamento status,
                                         LocalDateTime dataInicio, LocalDateTime dataFim, String search) {
        Specification<Agendamento> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();

            if (editorId != null) {
                predicates.add(cb.equal(root.get("editor").get("id"), editorId));
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
            ? usuarioRepository.findById(request.editorId())
                .orElseThrow(() -> new EditorNaoEncontradoException(request.editorId()))
            : null;

        if (request.dataHoraEnsaio().isBefore(LocalDateTime.now())) {
            throw new AgendamentoNoPassadoException();
        }

        var duracao = agendamento.getDuracaoMinutos();
        validarConflitoAgenda(pacote, request.dataHoraEnsaio(), duracao, request.localEnsaio(), agendamento.getId());

        var taxaDeslocamento = request.taxaDeslocamento() != null ? request.taxaDeslocamento() : BigDecimal.ZERO;

        var novoValorTotal = pacote.getValorBase().add(taxaDeslocamento);
        var novoValorEntradaExigido = pacote.getValorBase().multiply(new BigDecimal("0.30"))
            .setScale(2, RoundingMode.HALF_UP);
        var novoValorRestante = novoValorTotal.subtract(agendamento.getValorEntradaPago());
        var novoValorTotalFinal = novoValorTotal.add(agendamento.getValorExtras());

        agendamento.setPacote(pacote);
        agendamento.setEditor(editor);
        agendamento.setDataHoraEnsaio(request.dataHoraEnsaio());
        agendamento.setLocalEnsaio(request.localEnsaio());
        agendamento.setEnderecoCompleto(request.enderecoCompleto());
        agendamento.setTaxaDeslocamento(taxaDeslocamento);
        agendamento.setAutorizaUsoImagem(request.autorizaUsoImagem() != null ? request.autorizaUsoImagem() : agendamento.getAutorizaUsoImagem());
        agendamento.setObservacoes(request.observacoes());

        agendamento.setValorTotal(novoValorTotal);
        agendamento.setValorEntradaExigido(novoValorEntradaExigido);
        agendamento.setValorRestante(novoValorRestante);
        agendamento.setValorTotalFinal(novoValorTotalFinal);

        agendamento = agendamentoRepository.save(agendamento);
        return AgendamentoResponse.of(agendamento);
    }

    public DisponibilidadeResponse verificarDisponibilidade(LocalDate data, String hora, Integer duracaoMinutos, UUID excluirAgendamentoId) {
        var time = LocalTime.parse(hora, DateTimeFormatter.ofPattern("HH:mm"));
        var dataHora = LocalDateTime.of(data, time);
        var duracao = duracaoMinutos != null ? duracaoMinutos : 60;

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

        var novoFim = dataHora.plusMinutes(duracao);
        var conflitos = new ArrayList<DisponibilidadeResponse.Conflito>();

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

        return new DisponibilidadeResponse(conflitos.isEmpty(), conflitos);
    }

    public Agendamento registrarPagamentoFinal(UUID id, org.springframework.web.multipart.MultipartFile comprovante) {
        var agendamento = buscarPorId(id);

        if (comprovante != null && !comprovante.isEmpty()) {
            var url = fileStorageService.salvar(comprovante);
            agendamento.setUrlComprovanteFinal(url);
        }

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

    private Cliente resolverCliente(CriarAgendamentoCommand command) {
        if (command.clienteId() != null) {
            return clienteRepository.findById(command.clienteId())
                .orElseThrow(() -> new ClienteNaoEncontradoException(command.clienteId()));
        }

        if (command.telefone() != null && !command.telefone().isBlank()) {
            var clienteExistente = clienteRepository.findByTelefone(command.telefone());
            if (clienteExistente.isPresent()) {
                return clienteExistente.get();
            }
        }

        if (command.nome() == null || command.nome().isBlank()) {
            throw new IllegalArgumentException("Nome do cliente é obrigatório quando não informado um clienteId");
        }
        if (command.telefone() == null || command.telefone().isBlank()) {
            throw new IllegalArgumentException("Telefone do cliente é obrigatório quando não informado um clienteId");
        }

        OrigemCliente origemCliente = OrigemCliente.OUTROS;
        if (command.origem() != null && !command.origem().isBlank()) {
            try {
                origemCliente = OrigemCliente.valueOf(command.origem());
            } catch (IllegalArgumentException e) {
                origemCliente = OrigemCliente.OUTROS;
            }
        }

        var cliente = Cliente.builder()
            .nome(command.nome())
            .telefone(command.telefone())
            .email(command.email())
            .cpf(command.cpf())
            .cidade(command.cidade())
            .estado(command.estado())
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
