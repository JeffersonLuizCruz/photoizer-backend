package com.photoizer.crm.despesa.service;

import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.despesa.api.DespesaCategoriaRequest;
import com.photoizer.crm.despesa.api.DespesaRequest;
import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.despesa.model.DespesaCategoria;
import com.photoizer.crm.despesa.model.RecorrenciaDespesa;
import com.photoizer.crm.despesa.model.StatusDespesa;
import com.photoizer.crm.despesa.repository.DespesaCategoriaRepository;
import com.photoizer.crm.despesa.repository.DespesaRepository;
import com.photoizer.crm.shared.storage.FileStorageService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DespesaService {

    private final DespesaRepository despesaRepository;
    private final DespesaCategoriaRepository categoriaRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final FileStorageService fileStorageService;

    public DespesaService(DespesaRepository despesaRepository,
                          DespesaCategoriaRepository categoriaRepository,
                          AgendamentoRepository agendamentoRepository,
                          FileStorageService fileStorageService) {
        this.despesaRepository = despesaRepository;
        this.categoriaRepository = categoriaRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public List<Despesa> listar(LocalDate dataInicio, LocalDate dataFim, UUID categoriaId,
                                StatusDespesa status, UUID agendamentoId, UUID fotografoId,
                                String sortBy, String sortDir) {
        Specification<Despesa> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (dataInicio != null) predicates.add(cb.greaterThanOrEqualTo(root.get("data"), dataInicio));
            if (dataFim != null) predicates.add(cb.lessThanOrEqualTo(root.get("data"), dataFim));
            if (categoriaId != null) predicates.add(cb.equal(root.get("categoriaRef").get("id"), categoriaId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (agendamentoId != null) predicates.add(cb.equal(root.get("agendamentoId"), agendamentoId));
            if (fotografoId != null) predicates.add(cb.equal(root.get("fotografoId"), fotografoId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        var coluna = (sortBy != null && !sortBy.isBlank()) ? sortBy : "data";
        if (!List.of("data", "valor", "descricao", "categoria").contains(coluna)) coluna = "data";
        var direcao = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        var sort = Sort.by(direcao, coluna);
        return despesaRepository.findAll(spec, sort);
    }

    @Transactional(readOnly = true)
    public Despesa buscarPorId(UUID id) {
        return despesaRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Despesa não encontrada: " + id));
    }

    public Despesa criar(DespesaRequest request) {
        var categoria = resolverCategoria(request.categoriaId());
        var status = request.status() != null ? request.status() : StatusDespesa.PENDENTE;
        var recorrencia = request.recorrencia() != null ? request.recorrencia() : RecorrenciaDespesa.UNICA;

        if (request.agendamentoId() != null && !agendamentoRepository.existsById(request.agendamentoId())) {
            throw new IllegalArgumentException("Trabalho vinculado não encontrado: " + request.agendamentoId());
        }

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

        if (request.agendamentoId() != null && !agendamentoRepository.existsById(request.agendamentoId())) {
            throw new IllegalArgumentException("Trabalho vinculado não encontrado: " + request.agendamentoId());
        }

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
            throw new IllegalArgumentException("Despesa não encontrada: " + id);
        }
        despesaRepository.deleteById(id);
    }

    public Despesa vincularAgendamento(UUID id, UUID agendamentoId) {
        var despesa = buscarPorId(id);
        if (agendamentoId != null && !agendamentoRepository.existsById(agendamentoId)) {
            throw new IllegalArgumentException("Trabalho vinculado não encontrado: " + agendamentoId);
        }
        despesa.setAgendamentoId(agendamentoId);
        return despesaRepository.save(despesa);
    }

    public Despesa vincularFotografo(UUID id, UUID fotografoId) {
        var despesa = buscarPorId(id);
        despesa.setFotografoId(fotografoId);
        return despesaRepository.save(despesa);
    }

    public BigDecimal somarCustosFotografo(UUID agendamentoId, UUID fotografoId) {
        return despesaRepository.findByAgendamentoIdOrderByDataDesc(agendamentoId).stream()
            .filter(d -> fotografoId.equals(d.getFotografoId()))
            .map(Despesa::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal somarCustosTodosFotografos(UUID agendamentoId) {
        return despesaRepository.findByAgendamentoIdOrderByDataDesc(agendamentoId).stream()
            .filter(d -> d.getFotografoId() != null)
            .map(Despesa::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Despesa marcarComoPaga(UUID id) {
        var despesa = buscarPorId(id);
        if (despesa.getStatus() == StatusDespesa.RECORRENTE) {
            throw new IllegalArgumentException("Despesas recorrentes não podem ser marcadas como pagas. Marque a ocorrência gerada.");
        }
        despesa.setStatus(StatusDespesa.PAGO);
        despesa.setDataPagamento(LocalDateTime.now());
        return despesaRepository.save(despesa);
    }

    public Despesa anexarComprovante(UUID id, MultipartFile arquivo) {
        var despesa = buscarPorId(id);
        var caminho = fileStorageService.salvar(arquivo);
        despesa.setUrlComprovante(caminho);
        return despesaRepository.save(despesa);
    }

    @Transactional(readOnly = true)
    public List<Despesa> recorrentesProximas(int dias) {
        var limite = LocalDate.now().plusDays(Math.max(dias, 0));
        return despesaRepository
            .findByStatusAndDataProximaGeracaoNotNullAndDataProximaGeracaoLessThanEqual(
                StatusDespesa.RECORRENTE, limite)
            .stream()
            .sorted((a, b) -> a.getDataProximaGeracao().compareTo(b.getDataProximaGeracao()))
            .toList();
    }

    @Scheduled(cron = "0 5 6 * * *")
    @Transactional
    public void gerarDespesasRecorrentes() {
        var hoje = LocalDate.now();
        var pendentes = despesaRepository
            .findByStatusAndDataProximaGeracaoNotNullAndDataProximaGeracaoLessThanEqual(
                StatusDespesa.RECORRENTE, hoje);

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

    private LocalDate proximaGeracao(LocalDate data, RecorrenciaDespesa recorrencia) {
        return switch (recorrencia) {
            case ANUAL -> data.plusYears(1);
            case MENSAL -> data.plusMonths(1);
            case UNICA -> null;
        };
    }

    private DespesaCategoria resolverCategoria(UUID categoriaId) {
        if (categoriaId == null) {
            throw new IllegalArgumentException("Categoria é obrigatória");
        }
        return categoriaRepository.findById(categoriaId)
            .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada: " + categoriaId));
    }

    // ---- Categorias ----

    @Transactional(readOnly = true)
    public List<DespesaCategoria> listarCategorias(Boolean ativas) {
        return ativas != null && ativas
            ? categoriaRepository.findByAtivoTrueOrderByOrdemAscNomeAsc()
            : categoriaRepository.findAll().stream()
                .sorted((a, b) -> {
                    var cmp = Integer.compare(
                        a.getOrdem() != null ? a.getOrdem() : Integer.MAX_VALUE,
                        b.getOrdem() != null ? b.getOrdem() : Integer.MAX_VALUE);
                    return cmp != 0 ? cmp : a.getNome().compareToIgnoreCase(b.getNome());
                })
                .toList();
    }

    public DespesaCategoria criarCategoria(DespesaCategoriaRequest request) {
        categoriaRepository.findByNomeIgnoreCase(request.nome().trim())
            .ifPresent(c -> {
                throw new IllegalArgumentException("Já existe uma categoria com esse nome");
            });
        var categoria = DespesaCategoria.builder()
            .nome(request.nome().trim())
            .cor(request.cor())
            .ativo(request.ativo() != null ? request.ativo() : true)
            .ordem(request.ordem())
            .build();
        return categoriaRepository.save(categoria);
    }

    public DespesaCategoria atualizarCategoria(UUID id, DespesaCategoriaRequest request) {
        var categoria = categoriaRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada: " + id));
        var outro = categoriaRepository.findByNomeIgnoreCase(request.nome().trim());
        if (outro.isPresent() && !outro.get().getId().equals(id)) {
            throw new IllegalArgumentException("Já existe uma categoria com esse nome");
        }
        categoria.setNome(request.nome().trim());
        categoria.setCor(request.cor());
        categoria.setAtivo(request.ativo() != null ? request.ativo() : categoria.getAtivo());
        categoria.setOrdem(request.ordem());
        return categoriaRepository.save(categoria);
    }

    public void removerCategoria(UUID id) {
        var categoria = categoriaRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada: " + id));
        long qtd = despesaRepository.countByCategoriaRefId(id);
        if (qtd > 0) {
            categoria.setAtivo(false);
            categoriaRepository.save(categoria);
            return;
        }
        categoriaRepository.delete(categoria);
    }

    public long contarDespesas(UUID categoriaId) {
        return despesaRepository.countByCategoriaRefId(categoriaId);
    }

    public BigDecimal somarDespesasNoPeriodo(LocalDate inicio, LocalDate fim) {
        return despesaRepository.findByDataBetweenOrderByDataDesc(inicio, fim).stream()
            .map(Despesa::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
