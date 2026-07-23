package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.service.EcommerceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public AdminComprasController(EcommerceService ecommerceService) {
        this.ecommerceService = ecommerceService;
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
        var response = compras.map(CompraExtraResponse::of);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe da compra com fotos")
    public ResponseEntity<AdminCompraDetalheResponse> detalhe(@PathVariable UUID id) {
        return ResponseEntity.ok(ecommerceService.buscarCompraDetalhe(id));
    }

    @PatchMapping("/{id}/confirmar")
    @Operation(summary = "Confirmar pagamento da compra")
    public ResponseEntity<Void> confirmar(@PathVariable UUID id) {
        ecommerceService.confirmarPagamento(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar compra")
    public ResponseEntity<Void> cancelar(@PathVariable UUID id) {
        ecommerceService.cancelarCompra(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/relatorio")
    @Operation(summary = "Relatório financeiro do ecommerce")
    public ResponseEntity<AdminComprasRelatorioResponse> relatorio() {
        return ResponseEntity.ok(ecommerceService.gerarRelatorio());
    }
}
