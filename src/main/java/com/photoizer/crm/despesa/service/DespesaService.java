package com.photoizer.crm.despesa.service;

/*
 * REFACTORED — DespesaService
 *
 * Design Patterns aplicados:
 *
 * 1. Custom Exception Hierarchy (P1)
 *    Substitui IllegalArgumentException por exceções semânticas do domínio.
 *    Cada exceção mapeia para HTTP status correto no GlobalExceptionHandler
 *    (404 para "não encontrado", 409 para "conflito", 422 para "regra de negócio").
 *    Segue o padrão já estabelecido nos módulos agenda, cliente, edicao.
 *
 * 2. Specification Pattern (P2)
 *    Filros dinâmicos extraídos para DespesaSpecification (classe estática).
 *    Elimina Specification inline no service, facilitando testes e reuso.
 *    Whitelist de sort fica centralizada no Specification.
 *
 * 3. DRY — Deduplicação de queries de recorrência (P2)
 *    Método privado buscarRecorrentesVencidas() é reutilizado por
 *    recorrentesProximas() e gerarDespesasRecorrentes(), eliminando
 *    duplicação da mesma query com limites distintos.
 *
 * 4. Query Optimization — SQL SUM (P2)
 *    somarCustosFotografo, somarCustosTodosFotografos e somarDespesasNoPeriodo
 *    agora usam queries JPQL com COALESCE(SUM) em vez de carregar listas em memória.
 *    Elimina risco de OOM com volume crescente de despesas.
 *
 * 5. Facade Pattern — DespesaAgendamentoGateway (P2)
 *    Substitui injeção direta de AgendamentoRepository (módulo agenda) por
 *    uma porta/ACL que valida existência via repositório propio do módulo agenda.
 *    Remove acoplamento direto despesa→agenda (violação Modulith).
 */

import com.photoizer.crm.despesa.api.DespesaRequest;
import com.photoizer.crm.despesa.exception.AgendamentoVinculadoInvalidoException;
import com.photoizer.crm.despesa.exception.CategoriaDespesaNaoEncontradaException;
import com.photoizer.crm.despesa.exception.CategoriaObrigatoriaException;
import com.photoizer.crm.despesa.exception.DespesaNaoEncontradaException;
import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.despesa.model.DespesaCategoria;
import com.photoizer.crm.despesa.model.RecorrenciaDespesa;
import com.photoizer.crm.despesa.model.StatusDespesa;
import com.photoizer.crm.despesa.repository.DespesaCategoriaRepository;
import com.photoizer.crm.despesa.repository.DespesaRepository;
import com.photoizer.crm.despesa.repository.DespesaSpecification;
import com.photoizer.crm.shared.storage.FileStorageService;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DespesaService {

    private final DespesaRepository despesaRepository;
    private final DespesaCategoriaRepository categoriaRepository;
    private final DespesaAgendamentoGateway agendamentoGateway;
    private final FileStorageService fileStorageService;

    /*
     * REFACTORED — Injeção via Facade Pattern (DespesaAgendamentoGateway)
     *
     * Motivo: O módulo despesa não deveria importar AgendamentoRepository do agenda.
     * Isso viola o princípio de módulos do Spring Modulith (DEBT.md §7.4).
     * DespesaAgendamentoGateway é uma porta/ACL que encapsula a validação
     * de existência de agendamento, mantendo o contrato de dependência correto:
     * despesa → gateway (porta) ← agenda (adapter).
     */
    public DespesaService(DespesaRepository despesaRepository,
                          DespesaCategoriaRepository categoriaRepository,
                          DespesaAgendamentoGateway agendamentoGateway,
                          FileStorageService fileStorageService) {
        this.despesaRepository = despesaRepository;
        this.categoriaRepository = categoriaRepository;
        this.agendamentoGateway = agendamentoGateway;
        this.fileStorageService = fileStorageService;
    }

    /*
     * REFACTORED — Specification Pattern (DespesaSpecification)
     *
     * Motivo: A Specification inline no service (8 parâmetros + whitelist manual
     * de sort) é difícil de testar, reutilizar e manter. DespesaSpecification
     * encapsula as regras de busca em factory methods estáticos, seguindo o
     * padrão JPA Specification do Spring Data. A whitelist de colunas de sort
     * fica centralizada e validada por enum, não por string frágil.
     */
    @Transactional(readOnly = true)
    public List<Despesa> listar(LocalDate dataInicio, LocalDate dataFim, UUID categoriaId,
                                StatusDespesa status, UUID agendamentoId, UUID fotografoId,
                                String sortBy, String sortDir) {
        Specification<Despesa> spec = DespesaSpecification.comFiltros(dataInicio, dataFim, categoriaId, status, agendamentoId, fotografoId);
        Sort sort = DespesaSpecification.parseSort(sortBy, sortDir);
        return despesaRepository.findAll(spec, sort);
    }

    @Transactional(readOnly = true)
    public Despesa buscarPorId(UUID id) {
        return despesaRepository.findById(id)
            .orElseThrow(() -> new DespesaNaoEncontradaException(id));
    }

    public Despesa criar(DespesaRequest request) {
        var categoria = resolverCategoria(request.categoriaId());
        var status = request.status() != null ? request.status() : StatusDespesa.PENDENTE;
        var recorrencia = request.recorrencia() != null ? request.recorrencia() : RecorrenciaDespesa.UNICA;

        validarAgendamento(request.agendamentoId());

        var despesa = Despesa.builder()
            .descricao(request.descricao())
            .valor(request.valor())
            .categoria(categoria.getNome())
            .categoriaRef(categoria)
            .data(request.data())
            .formaPagamento(request.formaPagamento())
            .status(status)
            .recorrencia(recorrencia)
            .agendamentoId(request.agendamentoId())
            .fotografoId(request.fotografoId())
            .observacao(request.observacao())
            .build();

        if (status == StatusDespesa.RECORRENTE) {
            despesa.setDataProximaGeracao(proximaGeracao(request.data(), recorrencia));
        }

        return despesaRepository.save(despesa);
    }

    public Despesa atualizar(UUID id, DespesaRequest request) {
        var despesa = buscarPorId(id);
        var categoria = resolverCategoria(request.categoriaId());

        validarAgendamento(request.agendamentoId());

        var status = request.status() != null ? request.status() : StatusDespesa.PENDENTE;
        var recorrencia = request.recorrencia() != null ? request.recorrencia() : RecorrenciaDespesa.UNICA;

        despesa.setDescricao(request.descricao());
        despesa.setValor(request.valor());
        despesa.setCategoria(categoria.getNome());
        despesa.setCategoriaRef(categoria);
        despesa.setData(request.data());
        despesa.setFormaPagamento(request.formaPagamento());
        despesa.setStatus(status);
        despesa.setRecorrencia(recorrencia);
        despesa.setAgendamentoId(request.agendamentoId());
        despesa.setFotografoId(request.fotografoId());
        despesa.setObservacao(request.observacao());

        if (status == StatusDespesa.RECORRENTE) {
            despesa.setDataProximaGeracao(proximaGeracao(request.data(), recorrencia));
        } else {
            despesa.setDataProximaGeracao(null);
        }

        return despesaRepository.save(despesa);
    }

    public void remover(UUID id) {
        if (!despesaRepository.existsById(id)) {
            throw new DespesaNaoEncontradaException(id);
        }
        despesaRepository.deleteById(id);
    }

    public Despesa vincularAgendamento(UUID id, UUID agendamentoId) {
        var despesa = buscarPorId(id);
        validarAgendamento(agendamentoId);
        despesa.setAgendamentoId(agendamentoId);
        return despesaRepository.save(despesa);
    }

    public Despesa vincularFotografo(UUID id, UUID fotografoId) {
        var despesa = buscarPorId(id);
        despesa.setFotografoId(fotografoId);
        return despesaRepository.save(despesa);
    }

    /*
     * REFACTORED — SQL SUM Query (P2)
     *
     * Motivo: O método anterior carregava a lista inteira via findByAgendamentoIdOrderByDataDesc()
     * e somava com stream().reduce(). Com volume crescente de despesas, isso causa
     * OOM e é N+1 disfarçado. Agora usa JPQL com COALESCE(SUM) direto no banco.
     */
    public BigDecimal somarCustosFotografo(UUID agendamentoId, UUID fotografoId) {
        return despesaRepository.sumValorByAgendamentoIdAndFotografoId(agendamentoId, fotografoId);
    }

    public BigDecimal somarCustosTodosFotografos(UUID agendamentoId) {
        return despesaRepository.sumValorByAgendamentoIdWithFotografo(agendamentoId);
    }

    /*
     * REFACTORED — State Pattern (StatusDespesa)
     *
     * Motivo: A validação de transição de status era feita com if inline
     * no service. Agora o enum StatusDespesa encapsula as regras de
     * transição (quem pode ser pago, quem pode transicionar para PAGO),
     * seguindo o padrão já adotado em StatusCompraExtra e StatusEdicao.
     * Transições inválidas lançam StatusDespesaInvalidoException (409 CONFLICT).
     */
    public Despesa marcarComoPaga(UUID id) {
        var despesa = buscarPorId(id);
        var novoStatus = despesa.getStatus().transicionarParaPagamento();
        despesa.setStatus(novoStatus);
        despesa.setDataPagamento(LocalDateTime.now());
        return despesaRepository.save(despesa);
    }

    public Despesa anexarComprovante(UUID id, MultipartFile arquivo) {
        var despesa = buscarPorId(id);
        var caminho = fileStorageService.salvar(arquivo);
        despesa.setUrlComprovante(caminho);
        return despesaRepository.save(despesa);
    }

    /*
     * REFACTORED — DRY: Deduplicação de queries de recorrência (P2)
     *
     * Motivo: recorrentesProximas() e gerarDespesasRecorrentes() usavam a
     * mesma query com limites distintos. Agora compartilham buscarRecorrentesVencidas(),
     * eliminando duplicação e centralizando a lógica de busca de recorrentes.
     */
    @Transactional(readOnly = true)
    public List<Despesa> recorrentesProximas(int dias) {
        var limite = LocalDate.now().plusDays(Math.max(dias, 0));
        return buscarRecorrentesVencidas(limite);
    }

    @Scheduled(cron = "0 5 6 * * *")
    @Transactional
    public void gerarDespesasRecorrentes() {
        var pendentes = buscarRecorrentesVencidas(LocalDate.now());

        for (var origem : pendentes) {
            var gerada = Despesa.builder()
                .descricao(origem.getDescricao())
                .valor(origem.getValor())
                .categoria(origem.getCategoria())
                .categoriaRef(origem.getCategoriaRef())
                .data(origem.getDataProximaGeracao())
                .formaPagamento(origem.getFormaPagamento())
                .status(StatusDespesa.PENDENTE)
                .recorrencia(RecorrenciaDespesa.UNICA)
                .geradaDeId(origem.getId())
                .agendamentoId(origem.getAgendamentoId())
                .fotografoId(origem.getFotografoId())
                .observacao(origem.getObservacao())
                .build();
            despesaRepository.save(gerada);

            origem.setDataProximaGeracao(proximaGeracao(origem.getDataProximaGeracao(), origem.getRecorrencia()));
            despesaRepository.save(origem);
        }
    }

    private List<Despesa> buscarRecorrentesVencidas(LocalDate limite) {
        return despesaRepository
            .findByStatusAndDataProximaGeracaoNotNullAndDataProximaGeracaoLessThanEqual(
                StatusDespesa.RECORRENTE, limite)
            .stream()
            .sorted((a, b) -> a.getDataProximaGeracao().compareTo(b.getDataProximaGeracao()))
            .toList();
    }

    private LocalDate proximaGeracao(LocalDate data, RecorrenciaDespesa recorrencia) {
        return switch (recorrencia) {
            case ANUAL -> data.plusYears(1);
            case MENSAL -> data.plusMonths(1);
            case UNICA -> null;
        };
    }

    /*
     * REFACTORED — Facade Pattern (DespesaAgendamentoGateway)
     *
     * Motivo: Validação de existência de agendamento era feita injetando
     * AgendamentoRepository do módulo agenda diretamente. Isso viola Modulith.
     * DespesaAgendamentoGateway encapsula essa validação via porta/ACL,
     * mantendo o contrato correto de dependência entre módulos.
     */
    private void validarAgendamento(UUID agendamentoId) {
        if (agendamentoId != null && !agendamentoGateway.existsById(agendamentoId)) {
            throw new AgendamentoVinculadoInvalidoException(agendamentoId);
        }
    }

    private DespesaCategoria resolverCategoria(UUID categoriaId) {
        if (categoriaId == null) {
            throw new CategoriaObrigatoriaException();
        }
        return categoriaRepository.findById(categoriaId)
            .orElseThrow(() -> new CategoriaDespesaNaoEncontradaException(categoriaId));
    }

    public BigDecimal somarDespesasNoPeriodo(LocalDate inicio, LocalDate fim) {
        return despesaRepository.sumValorByDataBetween(inicio, fim);
    }
}
