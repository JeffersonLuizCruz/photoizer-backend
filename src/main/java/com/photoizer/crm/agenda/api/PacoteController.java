package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.Pacote;
import com.photoizer.crm.agenda.repository.PacoteRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pacotes")
@Tag(name = "Pacotes", description = "Gestão de pacotes de ensaio")
public class PacoteController {

    private final PacoteRepository pacoteRepository;

    public PacoteController(PacoteRepository pacoteRepository) {
        this.pacoteRepository = pacoteRepository;
    }

    @PostMapping
    @Operation(summary = "Criar pacote")
    public ResponseEntity<Pacote> criar(@Valid @RequestBody Pacote pacote) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pacoteRepository.save(pacote));
    }

    @GetMapping
    @Operation(summary = "Listar pacotes")
    public ResponseEntity<List<Pacote>> listar() {
        return ResponseEntity.ok(pacoteRepository.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar pacote por ID")
    public ResponseEntity<Pacote> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(pacoteRepository.findById(id).orElseThrow());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar pacote")
    public ResponseEntity<Pacote> atualizar(@PathVariable UUID id, @Valid @RequestBody Pacote dados) {
        var pacote = pacoteRepository.findById(id).orElseThrow();
        pacote.setNome(dados.getNome());
        pacote.setDescricao(dados.getDescricao());
        pacote.setQuantidadeFotos(dados.getQuantidadeFotos());
        pacote.setQuantidadeVideos(dados.getQuantidadeVideos());
        pacote.setValorBase(dados.getValorBase());
        pacote.setDuracaoEstimada(dados.getDuracaoEstimada());
        pacote.setBloqueiaDiaInteiro(dados.getBloqueiaDiaInteiro());
        pacote.setAtivo(dados.getAtivo());
        return ResponseEntity.ok(pacoteRepository.save(pacote));
    }
}
