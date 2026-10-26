package com.photoizer.crm.config.api;

import com.photoizer.crm.config.service.LogoContratadaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Serve a arte da marca usada na proposta pública.
 *
 * <p>O endpoint é público por design (a proposta é acessada por link sem login):
 * a regra de liberação está em {@code SecurityConfig}. Quando não existe upload
 * em Configurações, devolve a logo padrão empacotada na aplicação.
 */
@RestController
@RequestMapping("/api/v1/config")
@Tag(name = "Config", description = "Configuracoes globais do sistema")
public class LogoContratadaController {

    private final LogoContratadaService logoContratadaService;

    public LogoContratadaController(LogoContratadaService logoContratadaService) {
        this.logoContratadaService = logoContratadaService;
    }

    @GetMapping("/logo")
    @Operation(summary = "Obter a logo da contratada (pública)")
    public ResponseEntity<Resource> logo() {
        var logo = logoContratadaService.carregar();
        return ResponseEntity.ok()
            .contentType(logo.contentType())
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"" + logo.filename() + "\"")
            .body(logo.resource());
    }
}
