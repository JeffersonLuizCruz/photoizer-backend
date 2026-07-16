package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.event.AgendamentoCriadoEvent;
import com.photoizer.crm.agenda.exception.AgendamentoNaoEncontradoException;
import com.photoizer.crm.agenda.exception.AgendamentoNoPassadoException;
import com.photoizer.crm.agenda.exception.ConflitoDeAgendaException;
import com.photoizer.crm.agenda.exception.EditorNaoEncontradoException;
import com.photoizer.crm.agenda.exception.PacoteInativoException;
import com.photoizer.crm.agenda.exception.PacoteNaoEncontradoException;
import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.Pacote;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.agenda.repository.PacoteRepository;
import com.photoizer.crm.agenda.repository.UsuarioRepository;
import com.photoizer.crm.cliente.exception.ClienteNaoEncontradoException;
import com.photoizer.crm.cliente.model.Cliente;
import com.photoizer.crm.cliente.model.OrigemCliente;
import com.photoizer.crm.cliente.repository.ClienteRepository;
import com.photoizer.crm.shared.storage.FileStorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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

    public AgendamentoService(ClienteRepository clienteRepository,
                              PacoteRepository pacoteRepository,
                              UsuarioRepository usuarioRepository,
                              AgendamentoRepository agendamentoRepository,
                              FileStorageService fileStorageService,
                              ApplicationEventPublisher eventPublisher) {
        this.clienteRepository = clienteRepository;
        this.pacoteRepository = pacoteRepository;
        this.usuarioRepository = usuarioRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.fileStorageService = fileStorageService;
        this.eventPublisher = eventPublisher;
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
        var valorEntradaExigido = valorTotal.multiply(new BigDecimal("0.30"))
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
            .build();

        agendamento = agendamentoRepository.save(agendamento);

        eventPublisher.publishEvent(new AgendamentoCriadoEvent(
            agendamento.getId(),
            agendamento.getCliente().getId(),
            agendamento.getPacote().getId(),
            agendamento.getDataHoraEnsaio()
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
        }

        return agendamentoRepository.save(agendamento);
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

        return agendamentoRepository.save(agendamento);
    }

    public Agendamento toggleDestaque(UUID id) {
        var agendamento = buscarPorId(id);
        agendamento.setEnsaioDestaque(!agendamento.getEnsaioDestaque());
        return agendamentoRepository.save(agendamento);
    }

    public Agendamento registrarPagamentoFinal(UUID id, org.springframework.web.multipart.MultipartFile comprovante) {
        var agendamento = buscarPorId(id);

        if (comprovante != null && !comprovante.isEmpty()) {
            var url = fileStorageService.salvar(comprovante);
            agendamento.setUrlComprovanteFinal(url);
        }

        agendamento.setValorRestante(BigDecimal.ZERO);
        agendamento.setValorEntradaPago(agendamento.getValorTotalFinal());
        agendamento.setStatus(StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL);

        return agendamentoRepository.save(agendamento);
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
