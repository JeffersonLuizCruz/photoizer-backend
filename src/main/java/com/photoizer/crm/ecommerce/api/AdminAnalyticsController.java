package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.service.EcommerceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PATTERN: Service Layer
 * Controller delega toda a lógica de analytics para EcommerceQueryService,
 * eliminando o acesso direto a repositories (violacao de camadas anterior).
 */
@RestController
@RequestMapping("/api/v1/admin/ecommerce/analytics")
@Tag(name = "Admin Analytics", description = "Métricas e analytics do ecommerce")
public class AdminAnalyticsController {

    private final EcommerceQueryService ecommerceQueryService;

    public AdminAnalyticsController(EcommerceQueryService ecommerceQueryService) {
        this.ecommerceQueryService = ecommerceQueryService;
    }

    @GetMapping
    @Operation(summary = "Dashboard de analytics do ecommerce")
    public ResponseEntity<EcommerceAnalyticsResponse> analytics() {
        return ResponseEntity.ok(ecommerceQueryService.obterAnalytics());
    }
}
