package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.service.EcommerceService;
import com.photoizer.crm.foto.api.FotoEnsaioResponse;
import com.photoizer.crm.foto.service.FotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;

@RestController
@RequestMapping("/api/v1/ecommerce")
@Tag(name = "Ecommerce", description = "Galeria pública do cliente e seleção de fotos")
public class EcommerceController {

    private final EcommerceService ecommerceService;
    private final FotoService fotoService;

    public EcommerceController(EcommerceService ecommerceService, FotoService fotoService) {
        this.ecommerceService = ecommerceService;
        this.fotoService = fotoService;
    }

    @GetMapping("/galeria/{token}")
    @Operation(summary = "Listar fotos publicadas da galeria (via token)")
    public ResponseEntity<GaleriaResponse> galeria(@PathVariable UUID token) {
        var agendamento = ecommerceService.buscarAgendamento(token);
        var fotos = ecommerceService.listarFotosPublicadas(token);
        var response = new GaleriaResponse(
            fotos.stream().map(FotoEnsaioResponse::of).toList(),
            agendamento.getPacote().getQuantidadeFotos(),
            ecommerceService.getValorUnitarioFotoExtra()
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/galeria/{token}/selecionar")
    @Operation(summary = "Selecionar/deselecionar fotos para o pacote")
    public ResponseEntity<List<FotoEnsaioResponse>> selecionar(
            @PathVariable UUID token,
            @RequestBody SelecionarRequest request) {
        var fotos = ecommerceService.selecionarFotos(token, request.fotoIds(), request.selecionada());
        return ResponseEntity.ok(fotos.stream().map(FotoEnsaioResponse::of).toList());
    }

    @PostMapping("/galeria/{token}/checkout")
    @Operation(summary = "Finalizar compra de fotos extras")
    public ResponseEntity<CompraExtraResponse> checkout(
            @PathVariable UUID token,
            @RequestBody CheckoutRequest request) {
        var compra = ecommerceService.checkout(token, request.fotoIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(CompraExtraResponse.of(compra));
    }

    @PostMapping("/galeria/{token}/comprovante")
    @Operation(summary = "Enviar comprovante de pagamento das extras")
    public ResponseEntity<CompraExtraResponse> uploadComprovante(
            @PathVariable UUID token,
            @RequestParam UUID compraExtraId,
            @RequestParam MultipartFile comprovante) {
        var compra = ecommerceService.uploadComprovante(token, compraExtraId, comprovante);
        return ResponseEntity.ok(CompraExtraResponse.of(compra));
    }

    @PatchMapping("/admin/compras/{compraExtraId}/confirmar")
    @Operation(summary = "Confirmar pagamento da compra de extras (admin)")
    public ResponseEntity<Void> confirmarPagamento(@PathVariable UUID compraExtraId) {
        ecommerceService.confirmarPagamento(compraExtraId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/galeria/{token}/download/{fotoId}")
    @Operation(summary = "Baixar foto original (verifica permissão)")
    public ResponseEntity<Resource> downloadFoto(
            @PathVariable UUID token,
            @PathVariable UUID fotoId) {
        var originalPath = ecommerceService.downloadFoto(token, fotoId);
        var file = new FileSystemResource(originalPath);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + originalPath.getFileName().toString() + "\"")
            .contentType(MediaType.IMAGE_JPEG)
            .body(file);
    }

    @GetMapping("/galeria/{token}/download-zip")
    @Operation(summary = "Baixar todas as fotos liberadas em ZIP")
    public ResponseEntity<Resource> downloadZip(@PathVariable UUID token) {
        var zipPath = ecommerceService.downloadZip(token);
        var file = new FileSystemResource(zipPath);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fotos.zip\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(file);
    }

    @GetMapping("/fotos/{fotoId}/watermarked")
    @Operation(summary = "Servir foto com marca d'água (cache desabilitado)")
    public ResponseEntity<Resource> servirWatermarked(@PathVariable UUID fotoId) {
        var foto = fotoService.buscarPorId(fotoId);
        var file = new FileSystemResource(Path.of(foto.getWatermarkedPath()));
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.IMAGE_JPEG)
            .body(file);
    }
}
