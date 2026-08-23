package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.service.AvaliacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * PATTERN: Service Layer
 * Controller delega toda a lógica para AvaliacaoService,
 * eliminando o acesso direto ao repository.
 */
@RestController
@RequestMapping("/api/v1/avaliacoes")
@Tag(name = "Avaliações", description = "Avaliações e depoimentos")
public class AvaliacaoController {

    private final AvaliacaoService avaliacaoService;

    public AvaliacaoController(AvaliacaoService avaliacaoService) {
        this.avaliacaoService = avaliacaoService;
    }

    @PostMapping
    @Operation(summary = "Criar avaliação")
    public ResponseEntity<AvaliacaoResponse> criar(@Valid @RequestBody AvaliacaoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(avaliacaoService.criar(request));
    }

    @GetMapping("/depoimentos")
    @Operation(summary = "Listar depoimentos aprovados")
    public ResponseEntity<List<AvaliacaoResponse>> listarDepoimentos() {
        return ResponseEntity.ok(avaliacaoService.listarDepoimentos());
    }

    @GetMapping("/cliente/{clienteId}")
    @Operation(summary = "Listar avaliações do cliente")
    public ResponseEntity<List<AvaliacaoResponse>> listarPorCliente(@PathVariable UUID clienteId) {
        return ResponseEntity.ok(avaliacaoService.listarPorCliente(clienteId));
    }
}
