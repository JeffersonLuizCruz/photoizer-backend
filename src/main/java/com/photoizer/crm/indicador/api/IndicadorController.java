package com.photoizer.crm.indicador.api;

import com.photoizer.crm.comissao.service.ComissaoQueryService;
import com.photoizer.crm.indicador.model.Indicador;
import com.photoizer.crm.indicador.service.IndicadorCommand;
import com.photoizer.crm.indicador.service.IndicadorService;
import com.photoizer.crm.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller do módulo indicador.
 *
 * Pattern: Query Service Facade (Cross-Module Read)
 * O módulo comissao é dono dos dados de Indicacao. Em vez de injetar
 * IndicacaoRepository diretamente (violação Modulith + N+1), usamos
 * ComissaoQueryService como fachada de leitura, mantendo o isolamento
 * de módulos e resolvendo o N+1 com agregação em lote (1 query GROUP BY).
 */
@RestController
@RequestMapping("/api/v1/indicadores")
@Tag(name = "Indicadores", description = "CRUD de indicadores (pessoas que indicam clientes)")
@RolesAllowed({"ADMIN", "FOTOGRAFO", "EDITOR"})
public class IndicadorController {

    private final IndicadorService indicadorService;
    private final ComissaoQueryService comissaoQueryService;

    public IndicadorController(IndicadorService indicadorService,
                                ComissaoQueryService comissaoQueryService) {
        this.indicadorService = indicadorService;
        this.comissaoQueryService = comissaoQueryService;
    }

    @GetMapping
    @Operation(summary = "Listar indicadores com paginação e busca opcional")
    public ResponseEntity<PageResponse<IndicadorResponse>> listar(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int perPage,
            @RequestParam(defaultValue = "nome") String sortBy,
            @RequestParam(defaultValue = "asc") String sortOrder) {
        var sort = Sort.by(Sort.Direction.fromString(sortOrder), sortBy);
        var pageable = PageRequest.of(page - 1, perPage, sort);
        var indicadoresPage = indicadorService.listar(search, pageable);

        // Busca resumo de comissões em lote (1 query) — resolve N+1
        var indicadorIds = indicadoresPage.getContent().stream()
            .map(Indicador::getId)
            .toList();
        var resumoMap = comissaoQueryService.obterResumoPorIndicadores(indicadorIds);

        return ResponseEntity.ok(PageResponse.from(
            indicadoresPage.map(i -> toResponse(i, resumoMap)), page));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar indicador por ID")
    public ResponseEntity<IndicadorResponse> buscarPorId(@PathVariable UUID id) {
        var indicador = indicadorService.buscarPorId(id);
        var resumoMap = comissaoQueryService.obterResumoPorIndicadores(List.of(id));
        return ResponseEntity.ok(toResponse(indicador, resumoMap));
    }

    @PostMapping
    @Operation(summary = "Criar novo indicador")
    public ResponseEntity<IndicadorResponse> criar(@Valid @RequestBody IndicadorRequest request) {
        var command = new IndicadorCommand(
            request.nome(), request.telefone(),
            request.observacoes(), request.percentualComissao());
        var indicador = indicadorService.criar(command);
        var resumoMap = comissaoQueryService.obterResumoPorIndicadores(List.of(indicador.getId()));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(toResponse(indicador, resumoMap));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar indicador")
    public ResponseEntity<IndicadorResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody IndicadorRequest request) {
        var command = new IndicadorCommand(
            request.nome(), request.telefone(),
            request.observacoes(), request.percentualComissao());
        var indicador = indicadorService.atualizar(id, command);
        var resumoMap = comissaoQueryService.obterResumoPorIndicadores(List.of(id));
        return ResponseEntity.ok(toResponse(indicador, resumoMap));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover indicador")
    public ResponseEntity<Void> remover(@PathVariable UUID id) {
        indicadorService.remover(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Monta response enriquecida com totais de comissões via lookup em Map.
     * Substitui o N+1 anterior (1 query por indicador) por lookup O(1) em Map
     * pré-carregado com 1 query GROUP BY.
     */
    private IndicadorResponse toResponse(Indicador i,
                                          Map<UUID, ComissaoQueryService.IndicadorComissaoResumo> resumoMap) {
        var resumo = resumoMap.get(i.getId());
        var totalPendente = resumo != null ? resumo.totalPendente() : java.math.BigDecimal.ZERO;
        var totalPago = resumo != null ? resumo.totalPago() : java.math.BigDecimal.ZERO;
        var totalIndicacoes = resumo != null ? resumo.totalIndicacoes() : 0;
        return IndicadorResponse.of(i, totalPendente, totalPago, totalIndicacoes);
    }
}
