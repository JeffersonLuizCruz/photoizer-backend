package com.photoizer.crm.foto.api;

import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.service.FotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agendamentos/{agendamentoId}/fotos")
@Tag(name = "Fotos", description = "Gestão de fotos do ensaio")
public class FotoController {

    private final FotoService fotoService;

    public FotoController(FotoService fotoService) {
        this.fotoService = fotoService;
    }

    @PostMapping
    @Operation(summary = "Upload de fotos do ensaio")
    public ResponseEntity<List<FotoEnsaioResponse>> upload(
            @PathVariable UUID agendamentoId,
            @RequestParam("arquivos") List<MultipartFile> arquivos) {
        if (arquivos == null || arquivos.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var fotos = fotoService.uploadFotos(agendamentoId, arquivos);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(fotos.stream().map(FotoEnsaioResponse::of).toList());
    }

    @GetMapping
    @Operation(summary = "Listar fotos do ensaio")
    public ResponseEntity<List<FotoEnsaioResponse>> listar(@PathVariable UUID agendamentoId) {
        var fotos = fotoService.listar(agendamentoId);
        return ResponseEntity.ok(fotos.stream().map(FotoEnsaioResponse::of).toList());
    }

    @GetMapping("/{fotoId}/original")
    @Operation(summary = "Servir foto original")
    public ResponseEntity<Resource> servirOriginal(@PathVariable UUID fotoId) {
        var foto = fotoService.buscarPorId(fotoId);
        var file = new FileSystemResource(foto.getOriginalPath());
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .body(file);
    }

    @GetMapping("/{fotoId}/watermarked")
    @Operation(summary = "Servir foto com marca d'água")
    public ResponseEntity<Resource> servirWatermarked(@PathVariable UUID fotoId) {
        var foto = fotoService.buscarPorId(fotoId);
        var file = new FileSystemResource(Path.of(foto.getWatermarkedPath()));
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .body(file);
    }

    @GetMapping("/{fotoId}/thumb")
    @Operation(summary = "Servir thumbnail")
    public ResponseEntity<Resource> servirThumb(@PathVariable UUID fotoId) {
        var foto = fotoService.buscarPorId(fotoId);
        var file = new FileSystemResource(Path.of(foto.getThumbPath()));
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .body(file);
    }

    @PatchMapping("/publicar")
    @Operation(summary = "Publicar todas as fotos do ensaio")
    public ResponseEntity<List<FotoEnsaioResponse>> publicar(@PathVariable UUID agendamentoId) {
        var fotos = fotoService.publicar(agendamentoId);
        return ResponseEntity.ok(fotos.stream().map(FotoEnsaioResponse::of).toList());
    }

    @PatchMapping("/{fotoId}/metadata")
    @Operation(summary = "Atualizar metadados da foto (título, tags, categoria, destaque)")
    public ResponseEntity<FotoEnsaioResponse> atualizarMetadata(
            @PathVariable UUID fotoId,
            @org.springframework.web.bind.annotation.RequestBody FotoMetadataRequest request) {
        var foto = fotoService.atualizarMetadata(fotoId, request);
        return ResponseEntity.ok(FotoEnsaioResponse.of(foto));
    }

    @DeleteMapping("/{fotoId}")
    @Operation(summary = "Remover foto")
    public ResponseEntity<Void> deletar(@PathVariable UUID fotoId) {
        fotoService.deletar(fotoId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{fotoId}/visibilidade")
    @Operation(summary = "Alterar visibilidade da foto na loja")
    public ResponseEntity<FotoEnsaioResponse> alterarVisibilidade(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID fotoId,
            @RequestParam boolean visivel) {
        var foto = fotoService.alterarVisibilidade(agendamentoId, fotoId, visivel);
        return ResponseEntity.ok(FotoEnsaioResponse.of(foto));
    }

    @PatchMapping("/{fotoId}/status")
    @Operation(summary = "Alterar status individual da foto")
    public ResponseEntity<FotoEnsaioResponse> alterarStatus(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID fotoId,
            @RequestParam StatusFoto status) {
        var foto = fotoService.alterarStatus(agendamentoId, fotoId, status);
        return ResponseEntity.ok(FotoEnsaioResponse.of(foto));
    }

    @PutMapping("/{fotoId}/imagem")
    @Operation(summary = "Substituir arquivo de imagem da foto")
    public ResponseEntity<FotoEnsaioResponse> substituirImagem(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID fotoId,
            @RequestParam("arquivo") MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var foto = fotoService.substituirImagem(agendamentoId, fotoId, arquivo);
        return ResponseEntity.ok(FotoEnsaioResponse.of(foto));
    }
}
