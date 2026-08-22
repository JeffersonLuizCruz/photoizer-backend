package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.service.EcommerceService;
import com.photoizer.crm.ecommerce.service.PagamentoExtraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/ecommerce/compras")
@Tag(name = "Admin Compras", description = "Gestão administrativa de todas as compras de fotos extras")
public class AdminComprasController {

    private final EcommerceService ecommerceService;
    private final PagamentoExtraService pagamentoExtraService;

    public AdminComprasController(EcommerceService ecommerceService,
                                  PagamentoExtraService pagamentoExtraService) {
        this.ecommerceService = ecommerceService;
        this.pagamentoExtraService = pagamentoExtraService;
    }

    @GetMapping
    @Operation(summary = "Listar compras (paginado, filtrável por status e período)")
    public ResponseEntity<?> listar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataFim,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int perPage) {
        var compras = ecommerceService.listarComprasPaginado(status, dataInicio, dataFim, page, perPage);
        var response = compras.map(CompraExtraResponse::ofAdmin);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/comprovante")
    @Operation(summary = "Servir comprovante da compra (admin)")
    public ResponseEntity<Resource> comprovante(@PathVariable UUID id) {
        var comprovantePath = ecommerceService.buscarComprovantePathPorId(id);
        if (comprovantePath == null) {
            return ResponseEntity.notFound().build();
        }
        var file = new FileSystemResource(comprovantePath);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"comprovante\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(file);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe da compra com fotos")
    public ResponseEntity<AdminCompraDetalheResponse> detalhe(@PathVariable UUID id) {
        return ResponseEntity.ok(ecommerceService.buscarCompraDetalhe(id));
    }

    @PatchMapping("/{id}/confirmar")
    @Operation(summary = "Confirmar pagamento da compra")
    public ResponseEntity<Void> confirmar(@PathVariable UUID id) {
        pagamentoExtraService.confirmarPagamento(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar compra (com motivo opcional)")
    public ResponseEntity<Void> cancelar(@PathVariable UUID id,
                                         @Valid @RequestBody(required = false) CancelarCompraRequest request) {
        var motivo = request != null ? request.motivo() : null;
        pagamentoExtraService.cancelarCompra(id, motivo);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/relatorio")
    @Operation(summary = "Relatório financeiro do ecommerce")
    public ResponseEntity<AdminComprasRelatorioResponse> relatorio() {
        return ResponseEntity.ok(ecommerceService.gerarRelatorio());
    }
}
