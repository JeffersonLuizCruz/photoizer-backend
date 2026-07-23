package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.repository.PedidoRepository;
import com.photoizer.crm.ecommerce.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pedidos")
@Tag(name = "Pedidos", description = "Gestão de pedidos do ecommerce")
public class PedidoController {

    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidoService;

    public PedidoController(PedidoRepository pedidoRepository, PedidoService pedidoService) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoService = pedidoService;
    }

    @PostMapping
    @Operation(summary = "Criar pedido com cálculo automático de valores")
    public ResponseEntity<PedidoResponse> criar(@Valid @RequestBody PedidoRequest request) {
        var pedido = pedidoService.criar(request.clienteId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(PedidoResponse.of(pedido));
    }

    @GetMapping("/cliente/{clienteId}")
    @Operation(summary = "Listar pedidos do cliente")
    public ResponseEntity<List<PedidoResponse>> listarPorCliente(@PathVariable UUID clienteId) {
        var pedidos = pedidoRepository.findByClienteIdOrderByCreatedAtDesc(clienteId)
            .stream().map(PedidoResponse::of).toList();
        return ResponseEntity.ok(pedidos);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar pedido por ID")
    public ResponseEntity<PedidoResponse> buscarPorId(@PathVariable UUID id) {
        var pedido = pedidoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));
        return ResponseEntity.ok(PedidoResponse.of(pedido));
    }

    @PatchMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar pedido")
    public ResponseEntity<Void> cancelar(@PathVariable UUID id) {
        pedidoService.cancelar(id);
        return ResponseEntity.ok().build();
    }
}
