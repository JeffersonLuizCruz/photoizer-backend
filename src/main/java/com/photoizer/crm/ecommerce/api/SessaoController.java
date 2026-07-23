package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.Sessao;
import com.photoizer.crm.ecommerce.repository.SessaoRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessoes")
@Tag(name = "Sessões", description = "Sessões fotográficas")
public class SessaoController {

    private final SessaoRepository sessaoRepository;

    public SessaoController(SessaoRepository sessaoRepository) {
        this.sessaoRepository = sessaoRepository;
    }

    @PostMapping
    @Operation(summary = "Criar sessão")
    public ResponseEntity<SessaoResponse> criar(@Valid @RequestBody SessaoRequest request) {
        var sessao = Sessao.builder()
            .clienteId(request.clienteId())
            .nomeSessao(request.nomeSessao())
            .dataRealizacao(request.dataRealizacao())
            .local(request.local())
            .descricao(request.descricao())
            .status(request.status() != null ? request.status() : "ATIVA")
            .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(SessaoResponse.of(sessaoRepository.save(sessao)));
    }

    @GetMapping
    @Operation(summary = "Listar todas as sessões")
    public ResponseEntity<List<SessaoResponse>> listar() {
        return ResponseEntity.ok(sessaoRepository.findAll().stream().map(SessaoResponse::of).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar sessão por ID")
    public ResponseEntity<SessaoResponse> buscarPorId(@PathVariable UUID id) {
        var sessao = sessaoRepository.findById(id).orElseThrow(() -> new RuntimeException("Sessão não encontrada"));
        return ResponseEntity.ok(SessaoResponse.of(sessao));
    }
}
