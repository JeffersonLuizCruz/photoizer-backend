package com.photoizer.crm.cliente.api;

import com.photoizer.crm.agenda.api.AgendamentoResponse;
import com.photoizer.crm.agenda.service.AgendamentoService;
import com.photoizer.crm.cliente.model.Cliente;
import com.photoizer.crm.cliente.service.ClienteService;
import com.photoizer.crm.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clientes")
@Tag(name = "Clientes", description = "Gestão de clientes")
public class ClienteController {

    private final ClienteService clienteService;
    private final AgendamentoService agendamentoService;

    public ClienteController(ClienteService clienteService, AgendamentoService agendamentoService) {
        this.clienteService = clienteService;
        this.agendamentoService = agendamentoService;
    }

    @PostMapping
    @Operation(summary = "Criar cliente")
    public ResponseEntity<Cliente> criar(@Valid @RequestBody Cliente cliente) {
        var criado = clienteService.criar(cliente);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @GetMapping
    @Operation(summary = "Listar clientes", description = "Lista clientes com paginação e busca por nome ou telefone")
    public ResponseEntity<PageResponse<Cliente>> listar(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int perPage,
            @RequestParam(defaultValue = "nome") String sortBy,
            @RequestParam(defaultValue = "asc") String sortOrder) {
        var sort = Sort.by(Sort.Direction.fromString(sortOrder), sortBy);
        var pageable = PageRequest.of(page - 1, perPage, sort);
        var result = clienteService.listarPaginado(search, pageable);
        return ResponseEntity.ok(PageResponse.from(result, page));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar cliente por ID")
    public ResponseEntity<Cliente> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(clienteService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar cliente")
    public ResponseEntity<Cliente> atualizar(@PathVariable UUID id, @Valid @RequestBody Cliente cliente) {
        return ResponseEntity.ok(clienteService.atualizar(id, cliente));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir cliente")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        clienteService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/agendamentos")
    @Operation(summary = "Listar agendamentos do cliente")
    public ResponseEntity<List<AgendamentoResponse>> listarAgendamentos(@PathVariable UUID id) {
        var agendamentos = agendamentoService.listarPorClienteId(id);
        var response = agendamentos.stream().map(AgendamentoResponse::of).toList();
        return ResponseEntity.ok(response);
    }
}
