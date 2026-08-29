package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import com.photoizer.crm.ecommerce.service.CompraQueryService;
import com.photoizer.crm.ecommerce.service.EcommerceService;
import com.photoizer.crm.ecommerce.service.GaleriaQueryService;
import com.photoizer.crm.ecommerce.service.PagamentoExtraService;
import com.photoizer.crm.foto.api.FotoEnsaioResponse;
import com.photoizer.crm.foto.api.FotoMapper;
import com.photoizer.crm.foto.model.StatusFoto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/agendamentos/{agendamentoId}/ecommerce")
@RolesAllowed({"ADMIN", "FOTOGRAFO"})
@Tag(name = "Admin Ecommerce", description = "Gestão administrativa do ecommerce de fotos")
public class AdminEcommerceController {

    private final EcommerceService ecommerceService;
    private final GaleriaQueryService galeriaQueryService;
    private final CompraQueryService compraQueryService;
    private final EcommerceMapper ecommerceMapper;
    private final FotoMapper fotoMapper;
    private final PagamentoExtraService pagamentoExtraService;

    public AdminEcommerceController(EcommerceService ecommerceService,
                                    GaleriaQueryService galeriaQueryService,
                                    CompraQueryService compraQueryService,
                                    EcommerceMapper ecommerceMapper,
                                    FotoMapper fotoMapper,
                                    PagamentoExtraService pagamentoExtraService) {
        this.ecommerceService = ecommerceService;
        this.galeriaQueryService = galeriaQueryService;
        this.compraQueryService = compraQueryService;
        this.ecommerceMapper = ecommerceMapper;
        this.fotoMapper = fotoMapper;
        this.pagamentoExtraService = pagamentoExtraService;
    }

    @GetMapping
    @Operation(summary = "Resumo completo do ecommerce do agendamento")
    public ResponseEntity<AdminEcommerceResumoResponse> resumo(@PathVariable UUID agendamentoId) {
        var fotos = galeriaQueryService.listarFotosPorAgendamento(agendamentoId);
        var compras = compraQueryService.listarComprasPorAgendamento(agendamentoId);

        var totalFotos = fotos.size();
        var publicadas = (int) fotos.stream().filter(f -> f.getStatus() == StatusFoto.PUBLICADA).count();
        var selecionadas = (int) fotos.stream().filter(f -> f.isSelecionadaPacote()).count();
        var pagas = (int) fotos.stream().filter(f -> f.getStatus() == StatusFoto.PAGA).count();
        var aguardando = (int) fotos.stream()
            .filter(f -> f.getCompraExtraId() != null && f.getStatus() == StatusFoto.PUBLICADA)
            .count();

        var valorTotalExtras = compras.stream()
            .filter(c -> c.getStatus() == StatusCompraExtra.PAGA)
            .map(c -> c.getValorTotal())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var fotosResponse = fotos.stream().map(fotoMapper::toResponse).toList();
        var comprasResponse = compras.stream().map(ecommerceMapper::toAdminResponse).toList();

        return ResponseEntity.ok(new AdminEcommerceResumoResponse(
            totalFotos, publicadas, selecionadas, pagas, aguardando,
            fotosResponse, comprasResponse, valorTotalExtras, null,
            null
        ));
    }

    @PatchMapping("/fotos/{fotoId}/selecao")
    @Operation(summary = "Override admin de seleção de foto no pacote")
    public ResponseEntity<FotoEnsaioResponse> overrideSelecao(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID fotoId,
            @RequestParam boolean selecionada) {
        var foto = ecommerceService.overrideSelecao(agendamentoId, fotoId, selecionada);
        return ResponseEntity.ok(fotoMapper.toResponse(foto));
    }

    @PostMapping("/regen-token")
    @Operation(summary = "Regenerar token de acesso da galeria")
    public ResponseEntity<Void> regerarToken(@PathVariable UUID agendamentoId) {
        ecommerceService.regerarToken(agendamentoId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/compras/{compraExtraId}/confirmar")
    @Operation(summary = "Confirmar pagamento da compra de extras (admin)")
    public ResponseEntity<Void> confirmarPagamento(@PathVariable UUID compraExtraId) {
        pagamentoExtraService.confirmarPagamento(compraExtraId);
        return ResponseEntity.ok().build();
    }
}
