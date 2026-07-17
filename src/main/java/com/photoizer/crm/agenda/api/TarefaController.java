package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.service.TarefaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tarefas")
@Tag(name = "Tarefas", description = "Gestão de tarefas dos agendamentos")
public class TarefaController {

    private final TarefaService tarefaService;

    public TarefaController(TarefaService tarefaService) {
        this.tarefaService = tarefaService;
    }

    @GetMapping
    @Operation(summary = "Listar tarefas", description = "Lista tarefas, opcionalmente filtradas por agendamento")
    public ResponseEntity<List<TarefaResponse>> listar(
            @RequestParam(required = false) @Parameter(description = "Filtrar por ID do agendamento") UUID agendamentoId) {
        var tarefas = tarefaService.listar(agendamentoId).stream()
            .map(TarefaResponse::of)
            .toList();
        return ResponseEntity.ok(tarefas);
    }

    @PostMapping
    @Operation(summary = "Criar tarefa")
    public ResponseEntity<TarefaResponse> criar(@RequestBody Map<String, Object> body) {
        var agendamentoId = UUID.fromString((String) body.get("agendamentoId"));
        var tipo = (String) body.get("tipo");
        var responsavelId = body.get("responsavelId") != null
            ? UUID.fromString((String) body.get("responsavelId")) : null;
        var dataLimite = LocalDateTime.parse((String) body.get("dataLimite"));

        var tarefa = tarefaService.criar(agendamentoId, tipo, responsavelId, dataLimite);
        return ResponseEntity.status(HttpStatus.CREATED).body(TarefaResponse.of(tarefa));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar tarefa")
    public ResponseEntity<TarefaResponse> atualizar(
            @PathVariable UUID id,
            @RequestBody @Valid AtualizarTarefaRequest request) {
        return ResponseEntity.ok(tarefaService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir tarefa")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        tarefaService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Atualizar status da tarefa")
    public ResponseEntity<TarefaResponse> atualizarStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        var status = body.get("status");
        var tarefa = tarefaService.atualizarStatus(id, status);
        return ResponseEntity.ok(TarefaResponse.of(tarefa));
    }
}
