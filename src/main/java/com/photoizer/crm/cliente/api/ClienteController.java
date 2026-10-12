package com.photoizer.crm.cliente.api;

import com.photoizer.crm.cliente.api.dto.AtualizarClienteRequest;
import com.photoizer.crm.cliente.api.dto.ClienteAdminResponse;
import com.photoizer.crm.cliente.api.dto.CriarClienteRequest;
import com.photoizer.crm.cliente.service.ClienteService;
import com.photoizer.crm.shared.api.PageResponse;
import com.photoizer.crm.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
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

import java.util.UUID;

/**
 * Controller para gestão de clientes (admin).
 * USA DTOs para request/response - nunca entidades JPA.
 *
 * NOTA: Removida dependência com agenda (violação Modulith P1).
 * O endpoint de agendamentos do cliente deve ser movido para o módulo agenda.
 */
@RestController
@RequestMapping("/api/v1/clientes")
@RolesAllowed({"ADMIN", "AGENDADOR"})
@Tag(name = "Clientes", description = "Gestão de clientes (admin).")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    /**
     * Cria novo cliente.
     * Padrão DTO Pattern - recebe DTO de request, retorna DTO de response.
     */
    @PostMapping
    @Operation(operationId = "criarCliente", summary = "Criar cliente")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Cliente criado",
            content = @Content(schema = @Schema(implementation = ClienteAdminResponse.class))),
        @ApiResponse(responseCode = "409", description = "Cliente duplicado (telefone/CPF)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClienteAdminResponse> criar(@Valid @RequestBody CriarClienteRequest request) {
        var criado = clienteService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    /**
     * Lista clientes com paginação e busca.
     * Retorna DTOs de resposta admin (sem senhaHash).
     */
    @GetMapping
    @Operation(operationId = "listarClientes", summary = "Listar clientes",
        description = "Lista clientes com paginação e busca por nome ou telefone.")
    public ResponseEntity<PageResponse<ClienteAdminResponse>> listar(
            @RequestParam(required = false) @Parameter(description = "Busca por nome ou telefone") String search,
            @RequestParam(defaultValue = "1") @Parameter(description = "Página (1-based)") int page,
            @RequestParam(defaultValue = "10") @Parameter(description = "Itens por página") int perPage,
            @RequestParam(defaultValue = "nome") @Parameter(description = "Campo de ordenação") String sortBy,
            @RequestParam(defaultValue = "asc") @Parameter(description = "Direção (asc/desc)") String sortOrder) {
        var sort = Sort.by(Sort.Direction.fromString(sortOrder), sortBy);
        var pageable = PageRequest.of(page - 1, perPage, sort);
        var result = clienteService.listarPaginado(search, pageable);
        return ResponseEntity.ok(PageResponse.from(result, page));
    }

    /**
     * Busca cliente por ID.
     * Retorna DTO de resposta admin.
     */
    @GetMapping("/{id}")
    @Operation(operationId = "buscarClientePorId", summary = "Buscar cliente por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cliente encontrado",
            content = @Content(schema = @Schema(implementation = ClienteAdminResponse.class))),
        @ApiResponse(responseCode = "404", description = "Cliente não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClienteAdminResponse> buscarPorId(
            @PathVariable @Parameter(description = "ID do cliente") UUID id) {
        return ResponseEntity.ok(clienteService.buscarPorId(id));
    }

    /**
     * Atualiza cliente.
     * Padrão DTO Pattern - recebe DTO de request, retorna DTO de response.
     */
    @PutMapping("/{id}")
    @Operation(operationId = "atualizarCliente", summary = "Atualizar cliente")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cliente atualizado",
            content = @Content(schema = @Schema(implementation = ClienteAdminResponse.class))),
        @ApiResponse(responseCode = "404", description = "Cliente não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Cliente duplicado (telefone/CPF)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClienteAdminResponse> atualizar(
            @PathVariable @Parameter(description = "ID do cliente") UUID id,
            @Valid @RequestBody AtualizarClienteRequest request) {
        return ResponseEntity.ok(clienteService.atualizar(id, request));
    }

    /**
     * Exclui cliente.
     */
    @DeleteMapping("/{id}")
    @Operation(operationId = "deletarCliente", summary = "Excluir cliente")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Cliente excluído"),
        @ApiResponse(responseCode = "404", description = "Cliente não encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Cliente com vínculos impedindo exclusão",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deletar(@PathVariable @Parameter(description = "ID do cliente") UUID id) {
        clienteService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
