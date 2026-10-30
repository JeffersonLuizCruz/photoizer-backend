package com.photoizer.crm.foto.api;

import com.photoizer.crm.foto.exception.FotoNaoPertenceAoAgendamentoException;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.service.FotoPreviewService;
import com.photoizer.crm.foto.service.FotoService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agendamentos/{agendamentoId}/fotos")
@RolesAllowed({"ADMIN", "FOTOGRAFO", "EDITOR"})
@Tag(name = "Fotos", description = "Gestão de fotos do ensaio")
public class FotoController {

    private final FotoService fotoService;
    private final FotoMapper fotoMapper;
    private final FotoPreviewService fotoPreviewService;
    private final com.photoizer.crm.shared.storage.StoragePathResolver pathResolver;

    public FotoController(FotoService fotoService, FotoMapper fotoMapper,
                          FotoPreviewService fotoPreviewService,
                          com.photoizer.crm.shared.storage.StoragePathResolver pathResolver) {
        this.fotoService = fotoService;
        this.fotoMapper = fotoMapper;
        this.fotoPreviewService = fotoPreviewService;
        this.pathResolver = pathResolver;
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
            .body(fotos.stream().map(fotoMapper::toResponse).toList());
    }

    @GetMapping
    @Operation(summary = "Listar fotos do ensaio")
    public ResponseEntity<List<FotoEnsaioResponse>> listar(@PathVariable UUID agendamentoId) {
        var fotos = fotoService.listar(agendamentoId);
        return ResponseEntity.ok(fotos.stream().map(fotoMapper::toResponse).toList());
    }

    @GetMapping("/{fotoId}/original")
    @Operation(summary = "Servir foto original")
    public ResponseEntity<Resource> servirOriginal(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID fotoId) {
        var foto = fotoService.buscarParaArquivo(agendamentoId, fotoId);
        var file = new FileSystemResource(pathResolver.resolver(foto.getOriginalPath()));
        return ResponseEntity.ok()
            .contentType(resolverMediaType(foto.getFileName()))
            .body(file);
    }

    @GetMapping("/{fotoId}/watermarked")
    @Operation(summary = "Servir foto com marca d'água")
    public ResponseEntity<Resource> servirWatermarked(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID fotoId) {
        var foto = fotoService.buscarParaArquivo(agendamentoId, fotoId);
        var caminho = foto.getEditadaWatermarkedPath() != null
            ? foto.getEditadaWatermarkedPath()
            : foto.getWatermarkedPath();
        var file = new FileSystemResource(pathResolver.resolver(caminho));
        return ResponseEntity.ok()
            .contentType(resolverMediaType(foto.getFileName()))
            .body(file);
    }

    @GetMapping("/{fotoId}/thumb")
    @Operation(summary = "Servir thumbnail protegido para o staff")
    public ResponseEntity<Resource> servirThumb(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID fotoId) {
        var foto = fotoService.buscarParaArquivo(agendamentoId, fotoId);
        // O workspace do staff precisa exibir também fotos INEDITA. O endpoint
        // público só serve PUBLICADA/PAGA; aqui usamos o mesmo thumb protegido
        // da galeria (600x400), que também mantém o visual do cliente.
        var caminho = fotoPreviewService.caminhoThumb(foto);
        if (caminho == null) {
            caminho = foto.getEditadaThumbPath() != null
                ? foto.getEditadaThumbPath()
                : foto.getThumbPath();
        }
        if (caminho == null) {
            return ResponseEntity.notFound().build();
        }
        var file = new FileSystemResource(pathResolver.resolver(caminho));
        return ResponseEntity.ok()
            .contentType(resolverMediaType(caminho))
            .body(file);
    }

    @PatchMapping("/publicar")
    @Operation(summary = "Publicar todas as fotos do ensaio")
    public ResponseEntity<List<FotoEnsaioResponse>> publicar(@PathVariable UUID agendamentoId) {
        var fotos = fotoService.publicar(agendamentoId);
        return ResponseEntity.ok(fotos.stream().map(fotoMapper::toResponse).toList());
    }

    @PostMapping("/renumerar")
    @RolesAllowed({"ADMIN", "FOTOGRAFO"})
    @Operation(
        summary = "Renumerar a galeria sequencialmente",
        description = "Reatribui 1..N as fotos publicadas e visiveis. Permitido apenas "
            + "enquanto nenhuma foto foi selecionada, baixada, comprada ou vinculada a uma compra.")
    public ResponseEntity<List<FotoEnsaioResponse>> renumerar(@PathVariable UUID agendamentoId) {
        var fotos = fotoService.renumerar(agendamentoId);
        return ResponseEntity.ok(fotos.stream().map(fotoMapper::toResponse).toList());
    }

    @PatchMapping("/{fotoId}/metadata")
    @Operation(summary = "Atualizar metadados da foto (título, tags, categoria, destaque)")
    public ResponseEntity<FotoEnsaioResponse> atualizarMetadata(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID fotoId,
            @org.springframework.web.bind.annotation.RequestBody FotoMetadataRequest request) {
        buscarEValidarPropriedade(agendamentoId, fotoId);
        var foto = fotoService.atualizarMetadata(fotoId, request);
        return ResponseEntity.ok(fotoMapper.toResponse(foto));
    }

    @DeleteMapping("/{fotoId}")
    @Operation(operationId = "removerFoto", summary = "Remover foto")
    public ResponseEntity<Void> deletar(
            @Parameter(description = "ID do agendamento") @PathVariable UUID agendamentoId,
            @Parameter(description = "ID da foto") @PathVariable UUID fotoId) {
        fotoService.deletar(agendamentoId, fotoId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/remover-lote")
    @Operation(
        operationId = "removerFotosEmLote",
        summary = "Remover fotos em lote",
        description = "Remove permanentemente várias fotos do agendamento. A operação é atômica: "
            + "se alguma foto não existir, não pertencer ao agendamento ou estiver vinculada a "
            + "uma compra/pacote, nenhuma foto é removida.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Fotos removidas com sucesso"),
        @ApiResponse(responseCode = "403", description = "Foto não pertence ao agendamento",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Foto não encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Foto vinculada a compra ou pacote",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Lista de fotos inválida",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<FotosRemovidasResponse> removerEmLote(
            @Parameter(description = "ID do agendamento") @PathVariable UUID agendamentoId,
            @Valid @RequestBody RemoverFotosLoteRequest request) {
        var removidas = fotoService.deletarEmLote(agendamentoId, request.fotoIds());
        return ResponseEntity.ok(new FotosRemovidasResponse(removidas));
    }

    @PatchMapping("/{fotoId}/visibilidade")
    @Operation(summary = "Alterar visibilidade da foto na loja")
    public ResponseEntity<FotoEnsaioResponse> alterarVisibilidade(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID fotoId,
            @RequestParam boolean visivel) {
        var foto = fotoService.alterarVisibilidade(agendamentoId, fotoId, visivel);
        return ResponseEntity.ok(fotoMapper.toResponse(foto));
    }

    @PatchMapping("/{fotoId}/status")
    @Operation(summary = "Alterar status individual da foto")
    public ResponseEntity<FotoEnsaioResponse> alterarStatus(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID fotoId,
            @RequestParam StatusFoto status) {
        var foto = fotoService.alterarStatus(agendamentoId, fotoId, status);
        return ResponseEntity.ok(fotoMapper.toResponse(foto));
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
        return ResponseEntity.ok(fotoMapper.toResponse(foto));
    }

    @PutMapping("/{fotoId}/editada")
    @RolesAllowed({"ADMIN", "FOTOGRAFO", "EDITOR"})
    @Operation(
        summary = "Enviar a versão editada de uma foto",
        description = "Preserva o arquivo base, o número de galeria e o nome de exibição. "
            + "A versão só é exibida ao cliente quando a seleção é publicada.")
    public ResponseEntity<FotoEnsaioResponse> uploadEditada(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID fotoId,
            @RequestParam("arquivo") MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var foto = fotoService.uploadEditada(agendamentoId, fotoId, arquivo);
        return ResponseEntity.ok(fotoMapper.toResponse(foto));
    }

    @PutMapping("/{fotoId}/reedicao")
    @RolesAllowed({"ADMIN", "FOTOGRAFO", "EDITOR"})
    @Operation(
        summary = "Reenviar a edição publicada de uma foto",
        description = "Usado quando o cliente pede um ajuste depois de receber a foto. "
            + "Substitui a versão editada e libera imediatamente o novo download, "
            + "sem alterar o original, o número de galeria ou o nome.")
    public ResponseEntity<FotoEnsaioResponse> reeditar(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID fotoId,
            @RequestParam("arquivo") MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var foto = fotoService.reeditarFotoPublicada(agendamentoId, fotoId, arquivo);
        return ResponseEntity.ok(fotoMapper.toResponse(foto));
    }

    @PutMapping("/{fotoId}/editada-compra")
    @RolesAllowed({"ADMIN", "FOTOGRAFO", "EDITOR"})
    @Operation(
        summary = "Enviar a versão editada de uma foto extra comprada",
        description = "Use quando a foto foi comprada depois da publicação da galeria e "
            + "precisa ser editada sob demanda. A liberação do download é feita em "
            + "POST /{fotoId}/editada-compra/publicar.")
    public ResponseEntity<FotoEnsaioResponse> uploadEditadaCompraExtra(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID fotoId,
            @RequestParam("arquivo") MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var foto = fotoService.uploadEditadaCompraExtra(agendamentoId, fotoId, arquivo);
        return ResponseEntity.ok(fotoMapper.toResponse(foto));
    }

    @PostMapping("/{fotoId}/editada-compra/publicar")
    @RolesAllowed({"ADMIN", "FOTOGRAFO", "EDITOR"})
    @Operation(
        summary = "Liberar o download da edição de uma foto extra comprada",
        description = "Troca a versão pública para EDITADA depois que a versão editada "
            + "foi enviada. Requer compra paga e versão editada existente.")
    public ResponseEntity<FotoEnsaioResponse> publicarEditadaCompraExtra(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID fotoId) {
        var foto = fotoService.publicarEditadaCompraExtra(agendamentoId, fotoId);
        return ResponseEntity.ok(fotoMapper.toResponse(foto));
    }

    private FotoEnsaio buscarEValidarPropriedade(UUID agendamentoId, UUID fotoId) {
        var foto = fotoService.buscarPorId(fotoId);
        if (!foto.getAgendamentoId().equals(agendamentoId)) {
            throw new FotoNaoPertenceAoAgendamentoException(fotoId, agendamentoId);
        }
        return foto;
    }

    private MediaType resolverMediaType(String fileName) {
        if (fileName == null) return MediaType.IMAGE_JPEG;
        return switch (fileName.toLowerCase()) {
            case String s when s.endsWith(".png") -> MediaType.IMAGE_PNG;
            case String s when s.endsWith(".gif") -> MediaType.IMAGE_GIF;
            case String s when s.endsWith(".webp") -> MediaType.parseMediaType("image/webp");
            case String s when s.endsWith(".bmp") -> MediaType.parseMediaType("image/bmp");
            default -> MediaType.IMAGE_JPEG;
        };
    }
}
