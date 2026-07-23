package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.ItemCarrinho;
import com.photoizer.crm.ecommerce.model.MetodoPagamento;
import com.photoizer.crm.ecommerce.service.EcommerceService;
import com.photoizer.crm.foto.api.FotoEnsaioResponse;
import com.photoizer.crm.foto.service.FotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ecommerce")
@Tag(name = "Ecommerce", description = "Galeria pública do cliente e seleção de fotos")
public class EcommerceController {

    private static final String HEADER_SESSION = "X-Session-Id";

    private final EcommerceService ecommerceService;
    private final FotoService fotoService;

    public EcommerceController(EcommerceService ecommerceService, FotoService fotoService) {
        this.ecommerceService = ecommerceService;
        this.fotoService = fotoService;
    }

    private UUID resolverSessionId(@RequestHeader(HEADER_SESSION) UUID sessionId) {
        return sessionId;
    }

    @GetMapping("/galeria/{token}")
    @Operation(summary = "Listar fotos publicadas da galeria (via token)")
    public ResponseEntity<GaleriaResponse> galeria(@PathVariable UUID token) {
        var agendamento = ecommerceService.buscarAgendamento(token);
        var fotos = ecommerceService.listarFotosPublicadas(token);
        var response = new GaleriaResponse(
            fotos.stream().map(FotoEnsaioResponse::of).toList(),
            agendamento.getPacote().getQuantidadeFotos(),
            ecommerceService.getValorUnitarioFotoExtra(),
            agendamento.getPacote().getNome(),
            agendamento.getLocalEnsaio()
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

    @PostMapping("/galeria/{token}/carrinho")
    @Operation(summary = "Adicionar foto ao carrinho")
    public ResponseEntity<Void> adicionarAoCarrinho(
            @PathVariable UUID token,
            @RequestHeader(HEADER_SESSION) UUID sessionId,
            @RequestBody AdicionarAoCarrinhoRequest request) {
        ecommerceService.adicionarAoCarrinho(token, sessionId, request.fotoId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/galeria/{token}/carrinho/{fotoId}")
    @Operation(summary = "Remover foto do carrinho")
    public ResponseEntity<Void> removerDoCarrinho(
            @PathVariable UUID token,
            @RequestHeader(HEADER_SESSION) UUID sessionId,
            @PathVariable UUID fotoId) {
        ecommerceService.removerDoCarrinho(token, sessionId, fotoId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/galeria/{token}/carrinho")
    @Operation(summary = "Listar itens do carrinho")
    public ResponseEntity<CarrinhoResponse> listarCarrinho(
            @PathVariable UUID token,
            @RequestHeader(HEADER_SESSION) UUID sessionId) {
        var itens = ecommerceService.listarCarrinho(token, sessionId);
        var valorUnitario = ecommerceService.getValorUnitarioFotoExtra();
        var itensResponse = itens.stream()
            .map(ItemCarrinho::getFotoId)
            .map(fotoService::buscarPorId)
            .map(FotoEnsaioResponse::of)
            .map(fotoResponse -> CarrinhoItemResponse.of(fotoResponse, itens.size(), 0, valorUnitario))
            .toList();
        return ResponseEntity.ok(CarrinhoResponse.of(itensResponse, valorUnitario));
    }

    @GetMapping("/galeria/{token}/carrinho/quantidade")
    @Operation(summary = "Obter quantidade de itens no carrinho")
    public ResponseEntity<Integer> contarCarrinho(
            @PathVariable UUID token,
            @RequestHeader(HEADER_SESSION) UUID sessionId) {
        return ResponseEntity.ok(ecommerceService.contarCarrinho(token, sessionId));
    }

    @GetMapping("/galeria/{token}/calcular")
    @Operation(summary = "Calcular valor do carrinho (preview antes do checkout)")
    public ResponseEntity<CalculoCarrinhoResponse> calcular(
            @PathVariable UUID token,
            @RequestHeader(HEADER_SESSION) UUID sessionId) {
        return ResponseEntity.ok(ecommerceService.calcularCarrinho(token, sessionId));
    }

    @PostMapping("/galeria/{token}/checkout")
    @Operation(summary = "Finalizar compra usando itens do carrinho")
    public ResponseEntity<CompraExtraResponse> checkout(
            @PathVariable UUID token,
            @RequestHeader(HEADER_SESSION) UUID sessionId,
            @RequestBody(required = false) CheckoutRequest request) {
        var metodo = request != null && request.metodoPagamento() != null
            ? MetodoPagamento.valueOf(request.metodoPagamento().toUpperCase())
            : null;
        var compra = ecommerceService.checkout(token, sessionId, metodo);
        return ResponseEntity.status(HttpStatus.CREATED).body(CompraExtraResponse.of(compra));
    }

    @GetMapping("/galeria/{token}/compras")
    @Operation(summary = "Listar compras do agendamento (via token)")
    public ResponseEntity<List<CompraExtraResponse>> listarCompras(@PathVariable UUID token) {
        var compras = ecommerceService.listarComprasPorToken(token).stream()
            .map(CompraExtraResponse::of)
            .toList();
        return ResponseEntity.ok(compras);
    }

    @GetMapping("/galeria/{token}/compras/{compraId}")
    @Operation(summary = "Detalhe da compra com fotos (via token)")
    public ResponseEntity<AdminCompraDetalheResponse> detalheCompra(
            @PathVariable UUID token, @PathVariable UUID compraId) {
        return ResponseEntity.ok(ecommerceService.buscarCompraDetalhePorToken(token, compraId));
    }

    @GetMapping("/galeria/{token}/compras/{compraId}/comprovante")
    @Operation(summary = "Servir comprovante da compra (via token)")
    public ResponseEntity<Resource> comprovanteCompra(
            @PathVariable UUID token, @PathVariable UUID compraId) {
        var detalhe = ecommerceService.buscarCompraDetalhePorToken(token, compraId);
        if (detalhe.urlComprovante() == null) {
            return ResponseEntity.notFound().build();
        }
        var file = new FileSystemResource(detalhe.urlComprovante());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"comprovante\"")
            .body(file);
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

    @PostMapping("/galeria/{token}/favoritos/{fotoId}")
    @Operation(summary = "Adicionar foto aos favoritos (wishlist)")
    public ResponseEntity<Void> adicionarFavorito(
            @PathVariable UUID token,
            @RequestHeader(HEADER_SESSION) UUID sessionId,
            @PathVariable UUID fotoId) {
        ecommerceService.adicionarFavorito(token, sessionId, fotoId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/galeria/{token}/favoritos/{fotoId}")
    @Operation(summary = "Remover foto dos favoritos")
    public ResponseEntity<Void> removerFavorito(
            @PathVariable UUID token,
            @RequestHeader(HEADER_SESSION) UUID sessionId,
            @PathVariable UUID fotoId) {
        ecommerceService.removerFavorito(token, sessionId, fotoId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/galeria/{token}/favoritos")
    @Operation(summary = "Listar favoritos da sessão")
    public ResponseEntity<List<UUID>> listarFavoritos(
            @PathVariable UUID token,
            @RequestHeader(HEADER_SESSION) UUID sessionId) {
        return ResponseEntity.ok(ecommerceService.listarFavoritos(token, sessionId));
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
