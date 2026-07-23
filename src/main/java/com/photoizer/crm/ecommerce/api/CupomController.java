package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.Cupom;
import com.photoizer.crm.ecommerce.repository.CupomRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/cupons")
@Tag(name = "Cupons", description = "Gestão de cupons de desconto")
public class CupomController {

    private final CupomRepository cupomRepository;

    public CupomController(CupomRepository cupomRepository) {
        this.cupomRepository = cupomRepository;
    }

    @PostMapping
    @Operation(summary = "Criar cupom")
    public ResponseEntity<CupomResponse> criar(@Valid @RequestBody CupomRequest request) {
        var cupom = Cupom.builder()
            .codigo(request.codigo().toUpperCase())
            .descricao(request.descricao())
            .tipoDesconto(request.tipoDesconto())
            .valorDesconto(request.valorDesconto())
            .valorMinimoPedido(request.valorMinimoPedido())
            .usoLimite(request.usoLimite())
            .usosAtuais(0)
            .dataValidade(request.dataValidade())
            .ativo(request.ativo())
            .usoUnico(request.usoUnico())
            .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(CupomResponse.of(cupomRepository.save(cupom)));
    }

    @GetMapping
    @Operation(summary = "Listar todos os cupons")
    public ResponseEntity<List<CupomResponse>> listar() {
        return ResponseEntity.ok(cupomRepository.findAll().stream().map(CupomResponse::of).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar cupom por ID")
    public ResponseEntity<CupomResponse> buscarPorId(@PathVariable UUID id) {
        var cupom = cupomRepository.findById(id).orElseThrow(() -> new RuntimeException("Cupom não encontrado"));
        return ResponseEntity.ok(CupomResponse.of(cupom));
    }

    @PostMapping("/validar")
    @Operation(summary = "Validar cupom")
    public ResponseEntity<CupomValidacaoResponse> validar(@Valid @RequestBody CupomValidacaoRequest request) {
        var optCupom = cupomRepository.findByCodigoIgnoreCase(request.codigo());

        if (optCupom.isEmpty()) {
            return ResponseEntity.ok(new CupomValidacaoResponse(false, "Cupom não encontrado", null, request.codigo(), null, BigDecimal.ZERO, request.valorPedido()));
        }

        var cupom = optCupom.get();

        if (!cupom.getAtivo()) {
            return ResponseEntity.ok(new CupomValidacaoResponse(false, "Cupom inativo", cupom.getId(), cupom.getCodigo(), cupom.getTipoDesconto(), BigDecimal.ZERO, request.valorPedido()));
        }

        if (cupom.getDataValidade() != null && cupom.getDataValidade().isBefore(LocalDate.now())) {
            return ResponseEntity.ok(new CupomValidacaoResponse(false, "Cupom expirado", cupom.getId(), cupom.getCodigo(), cupom.getTipoDesconto(), BigDecimal.ZERO, request.valorPedido()));
        }

        if (cupom.getUsoLimite() != null && cupom.getUsosAtuais() >= cupom.getUsoLimite()) {
            return ResponseEntity.ok(new CupomValidacaoResponse(false, "Cupom esgotado", cupom.getId(), cupom.getCodigo(), cupom.getTipoDesconto(), BigDecimal.ZERO, request.valorPedido()));
        }

        BigDecimal valorPedido = request.valorPedido() != null ? request.valorPedido() : BigDecimal.ZERO;

        if (cupom.getValorMinimoPedido() != null && valorPedido.compareTo(cupom.getValorMinimoPedido()) < 0) {
            return ResponseEntity.ok(new CupomValidacaoResponse(false,
                "Valor mínimo de R$ " + cupom.getValorMinimoPedido() + " não atingido",
                cupom.getId(), cupom.getCodigo(), cupom.getTipoDesconto(), BigDecimal.ZERO, valorPedido));
        }

        BigDecimal valorDesconto;
        if ("PERCENTUAL".equals(cupom.getTipoDesconto())) {
            valorDesconto = valorPedido.multiply(cupom.getValorDesconto()).divide(BigDecimal.valueOf(100));
        } else {
            valorDesconto = cupom.getValorDesconto().min(valorPedido);
        }

        BigDecimal valorComDesconto = valorPedido.subtract(valorDesconto);

        return ResponseEntity.ok(new CupomValidacaoResponse(true, "Cupom válido!",
            cupom.getId(), cupom.getCodigo(), cupom.getTipoDesconto(), valorDesconto, valorComDesconto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir cupom")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        cupomRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
