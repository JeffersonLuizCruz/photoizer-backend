package com.photoizer.crm.financeiro.api;

import com.photoizer.crm.financeiro.model.StatusReceita;
import com.photoizer.crm.financeiro.model.TipoServico;
import com.photoizer.crm.financeiro.service.ReceitaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/financeiro/receitas")
@Tag(name = "Financeiro", description = "Controle de receitas e lançamentos")
public class ReceitaController {

    private final ReceitaService receitaService;

    public ReceitaController(ReceitaService receitaService) {
        this.receitaService = receitaService;
    }

    @GetMapping
    @Operation(summary = "Listar receitas com filtros e ordenação")
    public ResponseEntity<List<ReceitaResponse>> listar(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @RequestParam(required = false) StatusReceita status,
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) TipoServico tipoServico,
            @RequestParam(required = false) String formaPagamento,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        var receitas = receitaService
            .listar(dataInicio, dataFim, status, clienteId, tipoServico, formaPagamento, sortBy, sortDir)
            .stream().map(ReceitaResponse::of).toList();
        return ResponseEntity.ok(receitas);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar receita por ID")
    public ResponseEntity<ReceitaResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(ReceitaResponse.of(receitaService.buscarPorId(id)));
    }

    @PostMapping
    @Operation(summary = "Criar nova receita")
    public ResponseEntity<ReceitaResponse> criar(@Valid @RequestBody ReceitaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ReceitaResponse.of(receitaService.criar(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar receita")
    public ResponseEntity<ReceitaResponse> atualizar(@PathVariable UUID id,
                                                     @Valid @RequestBody ReceitaRequest request) {
        return ResponseEntity.ok(ReceitaResponse.of(receitaService.atualizar(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir receita")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        receitaService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/receber")
    @Operation(summary = "Marcar receita como recebida")
    public ResponseEntity<ReceitaResponse> receber(@PathVariable UUID id) {
        return ResponseEntity.ok(ReceitaResponse.of(receitaService.receber(id)));
    }

    @PostMapping("/{id}/duplicar")
    @Operation(summary = "Duplicar receita")
    public ResponseEntity<ReceitaResponse> duplicar(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ReceitaResponse.of(receitaService.duplicar(id)));
    }
}
