package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.Avaliacao;
import com.photoizer.crm.ecommerce.repository.AvaliacaoRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/avaliacoes")
@Tag(name = "Avaliações", description = "Avaliações e depoimentos")
public class AvaliacaoController {

    private final AvaliacaoRepository avaliacaoRepository;

    public AvaliacaoController(AvaliacaoRepository avaliacaoRepository) {
        this.avaliacaoRepository = avaliacaoRepository;
    }

    @PostMapping
    @Operation(summary = "Criar avaliação")
    public ResponseEntity<AvaliacaoResponse> criar(@Valid @RequestBody AvaliacaoRequest request) {
        var avaliacao = Avaliacao.builder()
            .clienteId(request.clienteId())
            .agendamentoId(request.agendamentoId())
            .pacoteId(request.pacoteId())
            .pontuacao(request.pontuacao())
            .comentario(request.comentario())
            .depoimento(request.depoimento())
            .aprovado(false)
            .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(AvaliacaoResponse.of(avaliacaoRepository.save(avaliacao)));
    }

    @GetMapping("/depoimentos")
    @Operation(summary = "Listar depoimentos aprovados")
    public ResponseEntity<List<AvaliacaoResponse>> listarDepoimentos() {
        return ResponseEntity.ok(avaliacaoRepository.findByAprovadoTrue().stream().map(AvaliacaoResponse::of).toList());
    }

    @GetMapping("/cliente/{clienteId}")
    @Operation(summary = "Listar avaliações do cliente")
    public ResponseEntity<List<AvaliacaoResponse>> listarPorCliente(@PathVariable UUID clienteId) {
        return ResponseEntity.ok(avaliacaoRepository.findByClienteId(clienteId).stream().map(AvaliacaoResponse::of).toList());
    }
}
