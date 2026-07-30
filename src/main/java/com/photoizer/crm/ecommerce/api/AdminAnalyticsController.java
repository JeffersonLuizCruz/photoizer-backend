package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/ecommerce/analytics")
@Tag(name = "Admin Analytics", description = "Métricas e analytics do ecommerce")
public class AdminAnalyticsController {

    private final CompraExtraRepository compraExtraRepository;
    private final FotoEnsaioRepository fotoEnsaioRepository;

    public AdminAnalyticsController(CompraExtraRepository compraExtraRepository,
                                    FotoEnsaioRepository fotoEnsaioRepository) {
        this.compraExtraRepository = compraExtraRepository;
        this.fotoEnsaioRepository = fotoEnsaioRepository;
    }

    @GetMapping
    @Operation(summary = "Dashboard de analytics do ecommerce")
    public ResponseEntity<EcommerceAnalyticsResponse> analytics() {
        var fotos = fotoEnsaioRepository.findAll();

        var receitaExtras = compraExtraRepository.totalPorStatus(StatusCompraExtra.PAGA);

        var totalSelecionadas = (int) fotos.stream().filter(f -> f.isSelecionadaPacote()).count();
        var totalVendidasExtras = (int) fotos.stream().filter(f -> f.getStatus() == StatusFoto.PAGA).count();
        var totalPublicadas = (int) fotos.stream()
            .filter(f -> f.getStatus() != StatusFoto.INEDITA)
            .count();

        var naoSelecionadas = totalPublicadas - totalSelecionadas;
        var taxaConversao = naoSelecionadas > 0
            ? (double) totalVendidasExtras / naoSelecionadas * 100.0
            : 0.0;

        List<EcommerceAnalyticsResponse.FotoPopularResponse> populares = fotos.stream()
            .filter(f -> f.isSelecionadaPacote() || f.getStatus() == StatusFoto.PAGA)
            .limit(20)
            .map(f -> new EcommerceAnalyticsResponse.FotoPopularResponse(
                f.getId().toString(),
                f.getFileName(),
                "/api/v1/agendamentos/" + f.getAgendamentoId() + "/fotos/" + f.getId() + "/thumb",
                f.isSelecionadaPacote(),
                f.getStatus() == StatusFoto.PAGA))
            .toList();

        return ResponseEntity.ok(new EcommerceAnalyticsResponse(
            receitaExtras,
            receitaExtras,
            totalSelecionadas,
            totalVendidasExtras,
            Math.round(taxaConversao * 100.0) / 100.0,
            populares
        ));
    }
}
