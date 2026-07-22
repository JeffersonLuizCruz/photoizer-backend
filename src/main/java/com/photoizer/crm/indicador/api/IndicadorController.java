package com.photoizer.crm.indicador.api;

import com.photoizer.crm.comissao.model.Indicacao;
import com.photoizer.crm.comissao.repository.IndicacaoRepository;
import com.photoizer.crm.indicador.model.Indicador;
import com.photoizer.crm.indicador.service.IndicadorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/indicadores")
@Tag(name = "Indicadores", description = "CRUD de indicadores (pessoas que indicam clientes)")
public class IndicadorController {

    private final IndicadorService indicadorService;
    private final IndicacaoRepository indicacaoRepository;

    public IndicadorController(IndicadorService indicadorService,
                               IndicacaoRepository indicacaoRepository) {
        this.indicadorService = indicadorService;
        this.indicacaoRepository = indicacaoRepository;
    }

    @GetMapping
    @Operation(summary = "Listar todos os indicadores (com busca opcional)")
    public ResponseEntity<List<IndicadorResponse>> listar(
            @RequestParam(required = false) String search) {
        var indicadores = indicadorService.listar(search);
        var responses = indicadores.stream()
            .map(this::toResponse)
            .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar indicador por ID")
    public ResponseEntity<IndicadorResponse> buscarPorId(@PathVariable UUID id) {
        var indicador = indicadorService.buscarPorId(id);
        return ResponseEntity.ok(toResponse(indicador));
    }

    @PostMapping
    @Operation(summary = "Criar novo indicador")
    public ResponseEntity<IndicadorResponse> criar(@Valid @RequestBody IndicadorRequest request) {
        var indicador = indicadorService.criar(request.nome(), request.telefone(), request.observacoes());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(indicador));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar indicador")
    public ResponseEntity<IndicadorResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody IndicadorRequest request) {
        var indicador = indicadorService.atualizar(id, request.nome(), request.telefone(), request.observacoes());
        return ResponseEntity.ok(toResponse(indicador));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover indicador")
    public ResponseEntity<Void> remover(@PathVariable UUID id) {
        indicadorService.remover(id);
        return ResponseEntity.noContent().build();
    }

    private IndicadorResponse toResponse(Indicador i) {
        var indicacoes = indicacaoRepository.findByIndicadorId(i.getId());
        var totalPendente = indicacoes.stream()
            .filter(ind -> "PENDENTE".equals(ind.getStatus()))
            .map(Indicacao::getValorComissao)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalPago = indicacoes.stream()
            .filter(ind -> "PAGA".equals(ind.getStatus()))
            .map(Indicacao::getValorComissao)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return IndicadorResponse.of(i, totalPendente, totalPago, indicacoes.size());
    }
}
