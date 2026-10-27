package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.event.AgendamentoCanceladoEvent;
import com.photoizer.crm.agenda.event.AgendamentoConfirmadoEvent;
import com.photoizer.crm.agenda.event.AgendamentoCriadoEvent;
import com.photoizer.crm.agenda.event.AgendamentoReatribuidoEvent;
import com.photoizer.crm.agenda.exception.AgendamentoNaoEncontradoException;
import com.photoizer.crm.agenda.exception.AgendamentoNoPassadoException;
import com.photoizer.crm.agenda.exception.EditorNaoEncontradoException;
import com.photoizer.crm.agenda.exception.FotografoNaoEncontradoException;
import com.photoizer.crm.agenda.exception.PropostaNaoEditavelException;
import com.photoizer.crm.agenda.exception.PropostaNaoRemovivelException;
import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import com.photoizer.crm.agenda.model.ReatribuicaoAgendamento;
import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.shared.model.TipoRepasse;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.agenda.repository.ReatribuicaoAgendamentoRepository;
import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.pacote.exception.PacoteInativoException;
import com.photoizer.crm.pacote.model.Pacote;
import com.photoizer.crm.pacote.service.PacoteQueryService;
import com.photoizer.crm.cliente.exception.ClienteNaoEncontradoException;
import com.photoizer.crm.cliente.model.Cliente;
import com.photoizer.crm.cliente.model.OrigemCliente;
import com.photoizer.crm.cliente.repository.ClienteRepository;
import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.shared.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.photoizer.crm.agenda.api.AtualizarAgendamentoRequest;
import com.photoizer.crm.agenda.api.AgendamentoMapper;
import com.photoizer.crm.agenda.api.AgendamentoResponse;
import com.photoizer.crm.agenda.api.AgendamentoClienteResponse;
import com.photoizer.crm.agenda.api.ReatribuicaoResponse;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

@Service
@Transactional
public class AgendamentoService {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoService.class);

    /**
     * Ensaios visiveis na area do cliente. A partir de CONFIRMADO; pre-reservas,
     * cancelados e no-show nao aparecem em "Meus Ensaios".
     */
    private static final List<StatusAgendamento> STATUS_VISIVEIS_CLIENTE = List.of(
        StatusAgendamento.CONFIRMADO,
        StatusAgendamento.REALIZADO,
        StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL,
        StatusAgendamento.EM_EDICAO,
        StatusAgendamento.FINALIZADO);

    private final ClienteRepository clienteRepository;
    private final PacoteQueryService pacoteQueryService;
    private final UserRepository userRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final ConfiguracaoService configuracaoService;
    private final AgendamentoFotografoRepository agendamentoFotografoRepository;
    private final ReatribuicaoAgendamentoRepository reatribuicaoAgendamentoRepository;
    private final DisponibilidadeService disponibilidadeService;
    private final PartilhaService partilhaService;
    private final AgendamentoValoresCalculator agendamentoValoresCalculator;
    private final AgendamentoMapper agendamentoMapper;

    public AgendamentoService(ClienteRepository clienteRepository,
                              PacoteQueryService pacoteQueryService,
                              UserRepository userRepository,
                              AgendamentoRepository agendamentoRepository,
                              ApplicationEventPublisher eventPublisher,
                              FotoEnsaioRepository fotoEnsaioRepository,
                              ConfiguracaoService configuracaoService,
                              AgendamentoFotografoRepository agendamentoFotografoRepository,
                              ReatribuicaoAgendamentoRepository reatribuicaoAgendamentoRepository,
                              DisponibilidadeService disponibilidadeService,
                              PartilhaService partilhaService,
                              AgendamentoValoresCalculator agendamentoValoresCalculator,
                              AgendamentoMapper agendamentoMapper) {
        this.clienteRepository = clienteRepository;
        this.pacoteQueryService = pacoteQueryService;
        this.userRepository = userRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.eventPublisher = eventPublisher;
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.configuracaoService = configuracaoService;
        this.agendamentoFotografoRepository = agendamentoFotografoRepository;
        this.reatribuicaoAgendamentoRepository = reatribuicaoAgendamentoRepository;
        this.disponibilidadeService = disponibilidadeService;
        this.partilhaService = partilhaService;
        this.agendamentoValoresCalculator = agendamentoValoresCalculator;
        this.agendamentoMapper = agendamentoMapper;
    }

    /**
     * Cria uma proposta/pré-reserva a partir do calendário. O agendamento nasce em
     * PRE_RESERVA (não ocupa agenda) e recebe o token do link público de assinatura.
     * O cliente é preenchido depois, pelo próprio cliente, no link.
     */
    public Agendamento criarProposta(CriarPropostaCommand command) {
        var pacote = pacoteQueryService.buscarEntityPorId(command.pacoteId());
        if (!pacote.getAtivo()) {
            throw new PacoteInativoException(pacote.getId());
        }

        var dataHoraEnsaio = command.dataHoraEnsaio() != null ? command.dataHoraEnsaio() : null;
        if (dataHoraEnsaio == null || dataHoraEnsaio.isBefore(LocalDateTime.now())) {
            throw new AgendamentoNoPassadoException();
        }

        var editor = (command.editorId() != null)
            ? userRepository.findById(command.editorId())
                .orElseThrow(() -> new EditorNaoEncontradoException(command.editorId()))
            : null;

        var fotografo = userRepository.findById(command.fotografoId())
            .orElseThrow(() -> new FotografoNaoEncontradoException(command.fotografoId()));
        if (fotografo.getPapel() != Papel.FOTOGRAFO) {
            throw new BadRequestException("O responsável selecionado não é um fotógrafo.");
        }

        var taxaDeslocamentoPadrao = configuracaoService.getValorDecimal(ConfigKey.TAXA_DESLOCAMENTO);
        var custoDeslocamento = command.custoDeslocamento() != null ? command.custoDeslocamento() : taxaDeslocamentoPadrao;
        var repassarDeslocamento = command.repassarDeslocamento() != null ? command.repassarDeslocamento() : true;
        var taxaDeslocamento = repassarDeslocamento ? custoDeslocamento : BigDecimal.ZERO;

        var percentualEntrada = configuracaoService.getValorDecimal(ConfigKey.PERCENTUAL_ENTRADA);
        var valores = agendamentoValoresCalculator.calcularValoresProposta(
            pacote.getValorBase(), taxaDeslocamento, percentualEntrada, command.valorEntradaPago());

        var duracao = command.duracaoMinutos() != null ? command.duracaoMinutos() : 60;

        disponibilidadeService.validarConflitoAgenda(
            pacote, dataHoraEnsaio, duracao, command.localEnsaio(),
            ConflitoAgendaParams.paraCriacao(command.fotografoId()));

        var agendamento = Agendamento.builder()
            .pacote(pacote)
            .editor(editor)
            .fotografo(fotografo)
            .dataHoraEnsaio(dataHoraEnsaio)
            .duracaoMinutos(duracao)
            .localEnsaio(command.localEnsaio())
            .valorTotal(valores.valorTotal())
            .valorEntradaExigido(valores.valorEntradaExigido())
            .valorEntradaPago(valores.valorEntradaPago())
            .valorRestante(valores.valorRestante())
            .valorExtras(BigDecimal.ZERO)
            .taxaDeslocamento(valores.taxaDeslocamento())
            .custoDeslocamento(custoDeslocamento)
            .repassarDeslocamento(repassarDeslocamento)
            .valorTotalFinal(valores.valorTotalFinal())
            .percentualEntrada(valores.percentualEntrada())
            .status(StatusAgendamento.PRE_RESERVA)
            .clausulasPersonalizadas(command.clausulasPersonalizadas())
            .autorizaUsoImagem(false)
            .ensaioDestaque(false)
            .observacoes(command.observacoes())
            .indicadorId(command.indicadorId())
            .indicadorNome(command.indicadorNome())
            .indicadorTelefone(command.indicadorTelefone())
            .tokenGaleria(UUID.randomUUID())
            .tokenExpiracao(LocalDateTime.now().plusDays(15))
            .build();

        agendamento = agendamentoRepository.save(agendamento);
        criarFotografosNoAgendamento(agendamento, command.fotografos());
        partilhaService.calcularPartilhaFotografo(agendamento);
        gerarTokenProposta(agendamento);
        agendamento.registrarEnvioProposta();
        agendamento = agendamentoRepository.save(agendamento);

        log.atInfo()
            .addKeyValue("event_action", "agendamento.proposta_criada")
            .addKeyValue("agendamento_id", agendamento.getId())
            .addKeyValue("pacote_id", pacote.getId())
            .addKeyValue("fotografo_id", fotografo.getId())
            .addKeyValue("valor_total", agendamento.getValorTotal())
            .log("Proposta de agendamento criada");
        return agendamento;
    }

    private void gerarTokenProposta(Agendamento agendamento) {
        var dias = configuracaoService.getValorInteiro(ConfigKey.CONTRATO_DIAS_VALIDADE);
        PropostaTokenUtils.gerar(agendamento, dias);
    }

    public Agendamento confirmarPagamento(UUID id) {
        var agendamento = agendamentoRepository.findByIdWithLock(id)
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(id));
        agendamento.confirmarPagamento();
        agendamento = agendamentoRepository.save(agendamento);
        log.atInfo()
            .addKeyValue("event_action", "agendamento.pagamento_entrada_confirmado")
            .addKeyValue("agendamento_id", agendamento.getId())
            .log("Pagamento da entrada confirmado");
        return agendamento;
    }

    public Agendamento aprovar(UUID id) {
        var agendamento = agendamentoRepository.findByIdWithLock(id)
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(id));

        var fotografoId = agendamento.getFotografo() != null ? agendamento.getFotografo().getId() : null;
        disponibilidadeService.validarConflitoAgenda(
            agendamento.getPacote(), agendamento.getDataHoraEnsaio(), agendamento.getDuracaoMinutos(),
            agendamento.getLocalEnsaio(),
            ConflitoAgendaParams.paraAtualizacao(fotografoId, agendamento.getId()));

        if (agendamento.getStatus() == StatusAgendamento.AGUARDANDO_APROVACAO
            && agendamento.entradaQuitada()) {
            agendamento.confirmarPagamento();
        }

        agendamento.aprovar();
        agendamento = agendamentoRepository.save(agendamento);

        publicarAgendamentoCriado(agendamento);
        eventPublisher.publishEvent(new AgendamentoConfirmadoEvent(
            agendamento.getId(),
            agendamento.getCliente() != null ? agendamento.getCliente().getId() : null
        ));
        log.atInfo()
            .addKeyValue("event_action", "agendamento.aprovado")
            .addKeyValue("agendamento_id", agendamento.getId())
            .addKeyValue("status", agendamento.getStatus())
            .log("Agendamento aprovado");
        return agendamento;
    }

    public Agendamento recusar(UUID id, String motivo, UUID autorId) {
        var agendamento = agendamentoRepository.findByIdWithLock(id)
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(id));

        agendamento.recusar(motivo);
        agendamento.setRecusadoPor(resolverNomeAutor(autorId));
        agendamento = agendamentoRepository.save(agendamento);

        eventPublisher.publishEvent(new AgendamentoCanceladoEvent(agendamento.getId()));
        log.atInfo()
            .addKeyValue("event_action", "agendamento.recusado")
            .addKeyValue("agendamento_id", agendamento.getId())
            .log("Proposta recusada");
        return agendamento;
    }

    private String resolverNomeAutor(UUID autorId) {
        if (autorId == null) {
            return null;
        }
        return userRepository.findById(autorId)
            .map(User::getNome)
            .orElse(autorId.toString());
    }

    private void publicarAgendamentoCriado(Agendamento agendamento) {
        var fotografoIds = agendamentoFotografoRepository.findByAgendamentoId(agendamento.getId())
            .stream().map(af -> af.getFotografo().getId()).toList();

        eventPublisher.publishEvent(new AgendamentoCriadoEvent(
            agendamento.getId(),
            agendamento.getCliente() != null ? agendamento.getCliente().getId() : null,
            agendamento.getPacote().getId(),
            agendamento.getDataHoraEnsaio(),
            agendamento.getIndicadorId(),
            agendamento.getIndicadorNome(),
            agendamento.getIndicadorTelefone(),
            null,
            agendamento.getPacote().getValorBase(),
            agendamento.nomeCliente(),
            fotografoIds
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
                predicates.add(cb.or(
                    cb.equal(root.get("fotografo").get("id"), fotografoId),
                    cb.exists(subquery)
                ));
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

    /**
     * Transfere o ensaio para outro fotógrafo responsável.
     * Não bloqueia por conflito de agenda (a decisão é do admin; o front apenas avisa),
     * mas exige que o ensaio ainda não tenha sido realizado.
     */
    public AgendamentoResponse reatribuirFotografo(UUID id, UUID novoFotografoId, String motivo, UUID solicitanteId) {
        var agendamento = agendamentoRepository.findByIdWithLock(id)
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(id));

        if (agendamento.getStatus() != StatusAgendamento.CONFIRMADO) {
            throw new BadRequestException("Só é possível transferir ensaios com status CONFIRMADO");
        }

        var novo = userRepository.findById(novoFotografoId)
            .orElseThrow(() -> new FotografoNaoEncontradoException(novoFotografoId));

        if (!novo.isAtivo()) {
            throw new BadRequestException("O fotógrafo selecionado está inativo");
        }
        if (novo.getPapel() != Papel.FOTOGRAFO && novo.getPapel() != Papel.ADMIN) {
            throw new BadRequestException("O responsável deve ser um fotógrafo ou administrador");
        }

        var anterior = agendamento.getFotografo();
        if (anterior != null && anterior.getId().equals(novo.getId())) {
            throw new BadRequestException("O agendamento já está atribuído a este fotógrafo");
        }

        var solicitante = solicitanteId != null
            ? userRepository.findById(solicitanteId).orElse(null)
            : null;

        agendamento.reatribuirFotografo(novo);
        agendamento = agendamentoRepository.save(agendamento);

        reatribuicaoAgendamentoRepository.save(
            ReatribuicaoAgendamento.registrar(agendamento, anterior, novo, solicitante, motivo));

        eventPublisher.publishEvent(new AgendamentoReatribuidoEvent(
            agendamento.getId(),
            agendamento.nomeCliente(),
            agendamento.getDataHoraEnsaio(),
            anterior != null ? anterior.getId() : null,
            novo.getId()
        ));

        var links = agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(agendamento.getId());
        return agendamentoMapper.toResponse(agendamento, links, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<ReatribuicaoResponse> listarReatribuicoes(UUID agendamentoId) {
        return reatribuicaoAgendamentoRepository.findByAgendamentoIdWithUsuarios(agendamentoId).stream()
            .map(ReatribuicaoResponse::of)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<Agendamento> listarPorClienteId(UUID clienteId) {
        return agendamentoRepository.findByClienteId(clienteId);
    }

    @Transactional(readOnly = true)
    public List<AgendamentoClienteResponse> listarAgendamentosCliente(UUID clienteId) {
        var agendamentos = agendamentoRepository
            .findByClienteIdAndStatusInOrderByDataHoraEnsaioDesc(clienteId, STATUS_VISIVEIS_CLIENTE);
        if (agendamentos.isEmpty()) {
            return List.of();
        }

        var ids = agendamentos.stream().map(Agendamento::getId).toList();
        var agregados = new HashMap<UUID, FotoEnsaioRepository.GaleriaPipelineFotos>();
        fotoEnsaioRepository.agregarPipeline(ids)
            .forEach(agregado -> agregados.put(agregado.getAgendamentoId(), agregado));

        var responses = new ArrayList<AgendamentoClienteResponse>(agendamentos.size());
        for (var agendamento : agendamentos) {
            var agregado = agregados.get(agendamento.getId());
            var totalPublicadas = agregado != null ? (int) agregado.getPublicadas() : 0;
            var selecionadasPacote = agregado != null ? (int) agregado.getSelecionadas() : 0;
            var pagas = agregado != null ? (int) agregado.getPagas() : 0;
            responses.add(AgendamentoClienteResponse.of(
                agendamento, totalPublicadas, selecionadasPacote, pagas));
        }
        return responses;
    }

    public AgendamentoResponse atualizar(UUID id, AtualizarAgendamentoRequest request) {
        var agendamento = buscarPorId(id);

        var pacote = pacoteQueryService.buscarEntityPorId(request.pacoteId());
        if (!pacote.getAtivo()) {
            throw new PacoteInativoException(pacote.getId());
        }

        var editor = request.editorId() != null
            ? userRepository.findById(request.editorId())
                .orElseThrow(() -> new EditorNaoEncontradoException(request.editorId()))
            : null;

        var fotografo = request.fotografoId() != null
            ? userRepository.findById(request.fotografoId()).orElse(null)
            : null;

        if (request.dataHoraEnsaio().isBefore(LocalDateTime.now())) {
            throw new AgendamentoNoPassadoException();
        }

        var duracao = agendamento.getDuracaoMinutos();
        disponibilidadeService.validarConflitoAgenda(
            pacote, request.dataHoraEnsaio(), duracao, request.localEnsaio(),
            ConflitoAgendaParams.paraAtualizacao(fotografo != null ? fotografo.getId() : null, agendamento.getId()));

        var taxaDeslocamentoPadrao = configuracaoService.getValorDecimal("taxaDeslocamentoPadrao", BigDecimal.ZERO);
        var custoDeslocamento = request.custoDeslocamento() != null ? request.custoDeslocamento() : taxaDeslocamentoPadrao;
        var repassarDeslocamento = request.repassarDeslocamento() != null ? request.repassarDeslocamento() : true;
        var taxaDeslocamento = repassarDeslocamento ? custoDeslocamento : BigDecimal.ZERO;

        agendamento.setPacote(pacote);
        agendamento.setEditor(editor);
        agendamento.setFotografo(fotografo);
        agendamento.setDataHoraEnsaio(request.dataHoraEnsaio());
        agendamento.setLocalEnsaio(request.localEnsaio());
        agendamento.setTaxaDeslocamento(taxaDeslocamento);
        agendamento.setCustoDeslocamento(custoDeslocamento);
        agendamento.setRepassarDeslocamento(repassarDeslocamento);
        agendamento.setAutorizaUsoImagem(request.autorizaUsoImagem() != null ? request.autorizaUsoImagem() : agendamento.getAutorizaUsoImagem());
        agendamento.setObservacoes(request.observacoes());

        if (Boolean.TRUE.equals(agendamento.getValoresPersonalizados())) {
            // Agendamento avulso com valor negociado: preserva o total/entrada
            // informados pelo staff, recalculando apenas o saldo.
            var valorTotalPersonalizado = agendamento.getValorTotal();
            var valorTotalFinal = valorTotalPersonalizado.add(agendamento.getValorExtras());
            agendamento.setValorTotalFinal(valorTotalFinal);
            agendamento.setValorRestante(valorTotalFinal.subtract(agendamento.getValorEntradaPago()).max(BigDecimal.ZERO));
        } else {
            var percentualEntrada = configuracaoService.getValorDecimal("percentualEntrada", new BigDecimal("30.00"));
            var valores = agendamentoValoresCalculator.calcularValoresAtualizacao(
                pacote.getValorBase(), taxaDeslocamento, percentualEntrada,
                agendamento.getValorEntradaPago(), agendamento.getValorExtras());

            agendamento.setValorTotal(valores.valorTotal());
            agendamento.setValorEntradaExigido(valores.valorEntradaExigido());
            agendamento.setPercentualEntrada(percentualEntrada);
            agendamento.setValorRestante(valores.valorRestante());
            agendamento.setValorTotalFinal(valores.valorTotalFinal());
        }

        agendamento = agendamentoRepository.save(agendamento);
        sincronizarFotografosNoAgendamento(agendamento, mapearRepasses(request.fotografos()));
        partilhaService.calcularPartilhaFotografo(agendamento);
        var links = agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(agendamento.getId());
        return agendamentoMapper.toResponse(agendamento, links, null, null, null);
    }

    /**
     * Altera uma proposta/pré-reserva ainda não assinada pelo cliente. Recalcula
     * valores, revalida conflito de agenda e sincroniza os repasses dos parceiros.
     * O token do link público é preservado (o link já enviado ao cliente continua
     * válido). Bloqueia a edição se a proposta já foi assinada ou decidida.
     */
    public AgendamentoResponse atualizarProposta(UUID id, CriarPropostaCommand command) {
        var agendamento = agendamentoRepository.findByIdWithLock(id)
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(id));

        if (agendamento.getStatus() != StatusAgendamento.PRE_RESERVA) {
            throw new PropostaNaoEditavelException(agendamento.getStatus());
        }

        var pacote = pacoteQueryService.buscarEntityPorId(command.pacoteId());
        if (!pacote.getAtivo()) {
            throw new PacoteInativoException(pacote.getId());
        }

        var dataHoraEnsaio = command.dataHoraEnsaio();
        if (dataHoraEnsaio == null || dataHoraEnsaio.isBefore(LocalDateTime.now())) {
            throw new AgendamentoNoPassadoException();
        }

        var editor = (command.editorId() != null)
            ? userRepository.findById(command.editorId())
                .orElseThrow(() -> new EditorNaoEncontradoException(command.editorId()))
            : null;

        var fotografo = userRepository.findById(command.fotografoId())
            .orElseThrow(() -> new FotografoNaoEncontradoException(command.fotografoId()));
        if (fotografo.getPapel() != Papel.FOTOGRAFO) {
            throw new BadRequestException("O responsável selecionado não é um fotógrafo.");
        }

        var duracao = command.duracaoMinutos() != null ? command.duracaoMinutos() : agendamento.getDuracaoMinutos();

        disponibilidadeService.validarConflitoAgenda(
            pacote, dataHoraEnsaio, duracao, command.localEnsaio(),
            ConflitoAgendaParams.paraAtualizacao(fotografo.getId(), agendamento.getId()));

        var taxaDeslocamentoPadrao = configuracaoService.getValorDecimal(ConfigKey.TAXA_DESLOCAMENTO);
        var custoDeslocamento = command.custoDeslocamento() != null ? command.custoDeslocamento() : taxaDeslocamentoPadrao;
        var repassarDeslocamento = command.repassarDeslocamento() != null ? command.repassarDeslocamento() : true;
        var taxaDeslocamento = repassarDeslocamento ? custoDeslocamento : BigDecimal.ZERO;

        var percentualEntrada = configuracaoService.getValorDecimal(ConfigKey.PERCENTUAL_ENTRADA);
        var valorEntradaPagoAtual = command.valorEntradaPago() != null
            ? (command.valorEntradaPago().signum() > 0 ? command.valorEntradaPago() : BigDecimal.ZERO)
            : agendamento.getValorEntradaPago();
        var valores = agendamentoValoresCalculator.calcularValoresAtualizacao(
            pacote.getValorBase(), taxaDeslocamento, percentualEntrada,
            valorEntradaPagoAtual, agendamento.getValorExtras(), command.valorEntradaPago());

        agendamento.setPacote(pacote);
        if (command.editorId() != null) {
            agendamento.setEditor(editor);
        }
        agendamento.setFotografo(fotografo);
        agendamento.setDataHoraEnsaio(dataHoraEnsaio);
        agendamento.setDuracaoMinutos(duracao);
        agendamento.setLocalEnsaio(command.localEnsaio());
        agendamento.setCustoDeslocamento(custoDeslocamento);
        agendamento.setRepassarDeslocamento(repassarDeslocamento);
        agendamento.setTaxaDeslocamento(taxaDeslocamento);
        if (command.clausulasPersonalizadas() != null) {
            agendamento.setClausulasPersonalizadas(command.clausulasPersonalizadas());
        }
        agendamento.setObservacoes(command.observacoes());
        if (command.indicadorId() != null
                || command.indicadorNome() != null
                || command.indicadorTelefone() != null) {
            agendamento.setIndicadorId(command.indicadorId());
            agendamento.setIndicadorNome(command.indicadorNome());
            agendamento.setIndicadorTelefone(command.indicadorTelefone());
        }

        agendamento.setValorTotal(valores.valorTotal());
        agendamento.setValorEntradaExigido(valores.valorEntradaExigido());
        agendamento.setValorEntradaPago(valores.valorEntradaPago());
        agendamento.setPercentualEntrada(valores.percentualEntrada());
        agendamento.setValorRestante(valores.valorRestante());
        agendamento.setValorTotalFinal(valores.valorTotalFinal());

        agendamento = agendamentoRepository.save(agendamento);
        sincronizarFotografosNoAgendamento(agendamento, command.fotografos());
        partilhaService.calcularPartilhaFotografo(agendamento);

        var links = agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(agendamento.getId());
        return agendamentoMapper.toResponse(agendamento, links, null, null, null);
    }

    /**
     * Remove uma proposta/pré-reserva de forma auditável (soft-cancel): move para
     * CANCELADO registrando motivo e autor, preservando histórico, vínculos e os
     * arquivos do termo/comprovante. Publica o evento de cancelamento para que os
     * módulos interessados (ex.: comissão) reconciliem o estado.
     */
    public Agendamento removerProposta(UUID id, String motivo, UUID autorId) {
        var agendamento = agendamentoRepository.findByIdWithLock(id)
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(id));

        if (!agendamento.getStatus().isPreReserva()) {
            throw new PropostaNaoRemovivelException(agendamento.getStatus());
        }

        var motivoFinal = (motivo == null || motivo.isBlank())
            ? "Proposta removida pelo staff"
            : motivo.trim();

        agendamento.recusar(motivoFinal);
        agendamento.setRecusadoPor(resolverNomeAutor(autorId));
        agendamento = agendamentoRepository.save(agendamento);

        eventPublisher.publishEvent(new AgendamentoCanceladoEvent(agendamento.getId()));
        log.atInfo()
            .addKeyValue("event_action", "agendamento.proposta_removida")
            .addKeyValue("agendamento_id", agendamento.getId())
            .log("Proposta de agendamento removida");
        return agendamento;
    }

    private List<CriarPropostaCommand.FotografoRepasse> mapearRepasses(
            List<AtualizarAgendamentoRequest.FotografoRepasse> fotografos) {
        if (fotografos == null) return null;
        return fotografos.stream()
            .map(f -> new CriarPropostaCommand.FotografoRepasse(
                f.fotografoId(), f.valorRepassar(), f.tipoValor(), f.percentual()))
            .toList();
    }

    private void criarFotografosNoAgendamento(Agendamento agendamento, List<CriarPropostaCommand.FotografoRepasse> fotografos) {
        if (fotografos == null) return;
        for (var f : fotografos) {
            var fotografo = userRepository.findById(f.fotografoId())
                .orElseThrow(() -> new FotografoNaoEncontradoException(f.fotografoId()));
            var tipo = f.tipoValor() != null ? f.tipoValor() : TipoRepasse.FIXO;
            var valorRepassar = agendamentoValoresCalculator.valorRepasseEfetivo(
                agendamento.getValorTotal(), tipo, f.valorRepassar(), f.percentual());
            validarValorRepasse(fotografo, valorRepassar);

            var link = AgendamentoFotografo.builder()
                .agendamento(agendamento)
                .fotografo(fotografo)
                .tipoValor(tipo)
                .percentual(tipo == TipoRepasse.PERCENTUAL ? f.percentual() : null)
                .papelParceiro(fotografo.getPapel())
                .valorRepassar(valorRepassar)
                .status(RepasseStatus.PENDENTE)
                .build();
            agendamentoFotografoRepository.save(link);
        }
    }

    private void validarValorRepasse(User fotografo, BigDecimal valorRepassar) {
        if (valorRepassar == null || valorRepassar.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(
                "O valor a repassar para o fotógrafo " + fotografo.getNome() + " deve ser maior que zero");
        }
    }

    private void sincronizarFotografosNoAgendamento(Agendamento agendamento, List<CriarPropostaCommand.FotografoRepasse> fotografos) {
        if (fotografos == null) return;
        var existentes = agendamentoFotografoRepository.findByAgendamentoId(agendamento.getId());

        var novosIds = fotografos.stream()
            .map(CriarPropostaCommand.FotografoRepasse::fotografoId)
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

    /**
     * Resolve/cria o cliente a partir dos dados informados pelo próprio cliente no
     * link público de assinatura (auto-cadastro), reutilizando dedup por telefone/CPF.
     */
    public Cliente resolverCliente(String nome, String telefone, String email,
                                   String cpf, String cidade, String estado) {
        var telefoneNormalizado = normalizarTelefone(telefone);
        var cpfNormalizado = normalizarCpf(cpf);

        if (telefoneNormalizado != null) {
            var porTelefone = clienteRepository.findByTelefone(telefoneNormalizado);
            if (porTelefone.isPresent()) {
                return porTelefone.get();
            }
        }
        if (cpfNormalizado != null) {
            var porCpf = clienteRepository.findByCpf(cpfNormalizado);
            if (porCpf.isPresent()) {
                return porCpf.get();
            }
        }
        var cliente = Cliente.builder()
            .nome(nome)
            .telefone(telefoneNormalizado)
            .email(email)
            .cpf(cpfNormalizado)
            .cidade(cidade)
            .estado(estado)
            .origem(OrigemCliente.OUTROS)
            .build();
        return clienteRepository.save(cliente);
    }

    /**
     * O auto-cadastro público pode receber o CPF sem máscara (ex.: preenchimento
     * por autofill/API). A entidade Cliente exige o formato 000.000.000-00, então
     * normalizamos os dígitos antes de persistir — evitando 500 de validação.
     */
    private String normalizarCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) return null;
        var digitos = cpf.replaceAll("\\D", "");
        if (digitos.length() != 11) {
            throw new BadRequestException("CPF inválido: informe 11 dígitos");
        }
        return digitos.replaceAll("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
    }

    private String normalizarTelefone(String telefone) {
        if (telefone == null || telefone.isBlank()) return null;
        return telefone.trim();
    }
}
