package com.photoizer.crm.edicao.api;

import com.photoizer.crm.edicao.model.StatusEdicao;
import com.photoizer.crm.edicao.service.EdicaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/edicao")
@Tag(name = "Edição", description = "Módulo de edição de fotos")
public class EdicaoController {

    private final EdicaoService edicaoService;

    public EdicaoController(EdicaoService edicaoService) {
        this.edicaoService = edicaoService;
    }

    @GetMapping
    @Operation(summary = "Listar todos os processos de edição")
    public ResponseEntity<List<EdicaoResponse>> listar(
            @RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) {
            var statusEnum = StatusEdicao.valueOf(status.toUpperCase());
            return ResponseEntity.ok(edicaoService.listarPorStatus(statusEnum));
        }
        return ResponseEntity.ok(edicaoService.listarTodos());
    }

    @GetMapping("/{agendamentoId}")
    @Operation(summary = "Obter status do processo de edição de um ensaio")
    public ResponseEntity<EdicaoResponse> obterStatus(@PathVariable UUID agendamentoId) {
        var response = edicaoService.obterStatus(agendamentoId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{agendamentoId}/fotos")
    @Operation(summary = "Listar fotos do processo de edição")
    public ResponseEntity<List<FotoEdicaoResponse>> listarFotos(@PathVariable UUID agendamentoId) {
        return ResponseEntity.ok(edicaoService.listarFotos(agendamentoId));
    }

    @PostMapping("/{agendamentoId}/raw")
    @Operation(summary = "Upload de fotos RAW (fotógrafo)")
    public ResponseEntity<List<FotoEdicaoResponse>> uploadRaw(
            @PathVariable UUID agendamentoId,
            @RequestParam("arquivos") List<MultipartFile> arquivos) {
        if (arquivos == null || arquivos.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var fotos = edicaoService.uploadRaw(agendamentoId, arquivos);
        return ResponseEntity.status(HttpStatus.CREATED).body(fotos);
    }

    @PostMapping("/{agendamentoId}/editadas")
    @Operation(summary = "Upload de fotos editadas (editor)")
    public ResponseEntity<List<FotoEdicaoResponse>> uploadEditadas(
            @PathVariable UUID agendamentoId,
            @RequestParam("arquivos") List<MultipartFile> arquivos) {
        if (arquivos == null || arquivos.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var fotos = edicaoService.uploadEditadas(agendamentoId, arquivos);
        return ResponseEntity.status(HttpStatus.CREATED).body(fotos);
    }

    @PatchMapping("/{agendamentoId}/concluir")
    @Operation(summary = "Concluir edição")
    public ResponseEntity<EdicaoResponse> concluirEdicao(@PathVariable UUID agendamentoId) {
        return ResponseEntity.ok(edicaoService.concluirEdicao(agendamentoId));
    }

    @PatchMapping("/{agendamentoId}/publicar")
    @Operation(summary = "Publicar fotos editadas no ecommerce")
    public ResponseEntity<List<FotoEdicaoResponse>> publicarNoEcommerce(@PathVariable UUID agendamentoId) {
        return ResponseEntity.ok(edicaoService.publicarNoEcommerce(agendamentoId));
    }

    @GetMapping("/fotos/{fotoId}/raw")
    @Operation(summary = "Servir arquivo RAW")
    public ResponseEntity<Resource> servirRaw(@PathVariable UUID fotoId) {
        var foto = edicaoService.buscarFoto(fotoId);
        var file = new FileSystemResource(foto.getRawPath());
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + foto.getRawFileName() + "\"")
            .body(file);
    }

    @GetMapping("/fotos/{fotoId}/edited")
    @Operation(summary = "Servir arquivo editado")
    public ResponseEntity<Resource> servirEdited(@PathVariable UUID fotoId) {
        var foto = edicaoService.buscarFoto(fotoId);
        if (foto.getEditedPath() == null) {
            return ResponseEntity.notFound().build();
        }
        var file = new FileSystemResource(foto.getEditedPath());
        var nome = foto.getEditedFileName() != null ? foto.getEditedFileName() : foto.getRawFileName();
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + nome + "\"")
            .body(file);
    }

    @DeleteMapping("/fotos/{fotoId}")
    @Operation(summary = "Deletar uma foto do processo de edição")
    public ResponseEntity<Void> deletarFoto(@PathVariable UUID fotoId) {
        edicaoService.deletarFoto(fotoId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/fotos/{fotoId}/raw-preview")
    @Operation(summary = "Preview da foto RAW (inline)")
    public ResponseEntity<Resource> previewRaw(@PathVariable UUID fotoId) {
        var foto = edicaoService.buscarFoto(fotoId);
        var file = new FileSystemResource(foto.getRawPath());
        return ResponseEntity.ok()
            .contentType(mediaTypeFromFilename(foto.getRawFileName()))
            .body(file);
    }

    @GetMapping("/fotos/{fotoId}/edited-preview")
    @Operation(summary = "Preview da foto editada (inline)")
    public ResponseEntity<Resource> previewEdited(@PathVariable UUID fotoId) {
        var foto = edicaoService.buscarFoto(fotoId);
        if (foto.getEditedPath() == null) {
            return ResponseEntity.notFound().build();
        }
        var file = new FileSystemResource(foto.getEditedPath());
        var nome = foto.getEditedFileName() != null ? foto.getEditedFileName() : foto.getRawFileName();
        return ResponseEntity.ok()
            .contentType(mediaTypeFromFilename(nome))
            .body(file);
    }

    @PatchMapping("/{agendamentoId}/observacoes")
    @Operation(summary = "Atualizar observacoes da edicao")
    public ResponseEntity<EdicaoResponse> atualizarObservacoes(
            @PathVariable UUID agendamentoId,
            @RequestBody Map<String, String> body) {
        var observacoes = body.getOrDefault("observacoes", "");
        var response = edicaoService.atualizarObservacoes(agendamentoId, observacoes);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/fotos/reordenar")
    @Operation(summary = "Reordenar fotos da edicao")
    public ResponseEntity<List<FotoEdicaoResponse>> reordenarFotos(
            @RequestBody List<Map<String, Object>> fotos) {
        var response = edicaoService.reordenarFotos(fotos);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{agendamentoId}/download-raw")
    @Operation(summary = "Download de todas as RAW em ZIP")
    public ResponseEntity<Resource> downloadRawZip(@PathVariable UUID agendamentoId) {
        try {
            var zipPath = edicaoService.gerarZipRaw(agendamentoId);
            var resource = new FileSystemResource(zipPath.toFile());
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"raw_" + agendamentoId + ".zip\"")
                .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{agendamentoId}/download-editadas")
    @Operation(summary = "Download de todas as editadas em ZIP")
    public ResponseEntity<Resource> downloadEditadasZip(@PathVariable UUID agendamentoId) {
        try {
            var zipPath = edicaoService.gerarZipEditadas(agendamentoId);
            var resource = new FileSystemResource(zipPath.toFile());
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"editadas_" + agendamentoId + ".zip\"")
                .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private MediaType mediaTypeFromFilename(String filename) {
        if (filename == null) return MediaType.APPLICATION_OCTET_STREAM;
        var lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MediaType.valueOf("image/webp");
        return MediaType.IMAGE_JPEG;
    }
}
