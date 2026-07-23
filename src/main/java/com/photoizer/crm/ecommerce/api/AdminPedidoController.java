package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.repository.PedidoRepository;
import com.photoizer.crm.ecommerce.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/pedidos")
@Tag(name = "Admin Pedidos", description = "Gestão administrativa de pedidos")
public class AdminPedidoController {

    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidoService;

    public AdminPedidoController(PedidoRepository pedidoRepository, PedidoService pedidoService) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoService = pedidoService;
    }

    @GetMapping
    @Operation(summary = "Listar pedidos (paginado)")
    public ResponseEntity<Page<PedidoResponse>> listar(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int perPage) {
        var pageable = PageRequest.of(page - 1, perPage, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PedidoResponse> result;
        if (status != null && !status.isBlank()) {
            result = pedidoRepository.findByStatus(status, pageable).map(PedidoResponse::of);
        } else {
            result = pedidoRepository.findAll(pageable).map(PedidoResponse::of);
        }
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Atualizar status do pedido")
    public ResponseEntity<PedidoResponse> atualizarStatus(@PathVariable UUID id, @RequestParam String status) {
        var pedido = pedidoService.atualizarStatus(id, status);
        return ResponseEntity.ok(PedidoResponse.of(pedido));
    }
}
