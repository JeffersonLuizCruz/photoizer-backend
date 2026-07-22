package com.photoizer.crm.pacote.api;

import com.photoizer.crm.pacote.service.PacoteService;
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
@RequestMapping("/api/v1/pacotes")
@Tag(name = "Pacotes", description = "Gestão de pacotes de ensaio")
public class PacoteController {

    private final PacoteService pacoteService;

    public PacoteController(PacoteService pacoteService) {
        this.pacoteService = pacoteService;
    }

    @PostMapping
    @Operation(summary = "Criar pacote")
    public ResponseEntity<PacoteResponse> criar(@Valid @RequestBody PacoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pacoteService.criar(request));
    }

    @GetMapping
    @Operation(summary = "Listar pacotes")
    public ResponseEntity<?> listar(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int perPage,
            @RequestParam(defaultValue = "nome") String sortBy,
            @RequestParam(defaultValue = "asc") String sortOrder) {
        var sort = Sort.by(Sort.Direction.fromString(sortOrder), sortBy);
        var pageable = PageRequest.of(page - 1, perPage, sort);
        var result = pacoteService.listarPaginado(search, pageable);
        return ResponseEntity.ok(PageResponse.from(result, page));
    }

    @GetMapping("/all")
    @Operation(summary = "Listar todos os pacotes (sem paginação)")
    public ResponseEntity<List<PacoteResponse>> listarTodos() {
        return ResponseEntity.ok(pacoteService.listarTodos());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar pacote por ID")
    public ResponseEntity<PacoteResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(pacoteService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar pacote")
    public ResponseEntity<PacoteResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody PacoteRequest request) {
        return ResponseEntity.ok(pacoteService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir pacote")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        pacoteService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
