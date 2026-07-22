package com.photoizer.crm.despesa.api;

import com.photoizer.crm.despesa.service.DespesaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/despesas")
@Tag(name = "Despesas", description = "Despesas manuais (manutenção, compras etc.)")
public class DespesaController {

    private final DespesaService despesaService;

    public DespesaController(DespesaService despesaService) {
        this.despesaService = despesaService;
    }

    @GetMapping
    @Operation(summary = "Listar despesas (com filtro opcional de data)")
    public ResponseEntity<List<DespesaResponse>> listar(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim) {
        var despesas = despesaService.listar(dataInicio, dataFim).stream()
            .map(DespesaResponse::of)
            .toList();
        return ResponseEntity.ok(despesas);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar despesa por ID")
    public ResponseEntity<DespesaResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(DespesaResponse.of(despesaService.buscarPorId(id)));
    }

    @PostMapping
    @Operation(summary = "Criar nova despesa")
    public ResponseEntity<DespesaResponse> criar(@Valid @RequestBody DespesaRequest request) {
        var despesa = despesaService.criar(
            request.descricao(), request.valor(), request.categoria(),
            request.data(), request.observacao()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(DespesaResponse.of(despesa));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar despesa")
    public ResponseEntity<DespesaResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody DespesaRequest request) {
        var despesa = despesaService.atualizar(
            id, request.descricao(), request.valor(), request.categoria(),
            request.data(), request.observacao()
        );
        return ResponseEntity.ok(DespesaResponse.of(despesa));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover despesa")
    public ResponseEntity<Void> remover(@PathVariable UUID id) {
        despesaService.remover(id);
        return ResponseEntity.noContent().build();
    }
}
