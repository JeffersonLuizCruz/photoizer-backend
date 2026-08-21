package com.photoizer.crm.config.api;

import com.photoizer.crm.config.service.ConfiguracaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller de configuracoes globais do sistema.
 *
 * <p>Nota sobre Modulith: endpoints de template de contrato foram movidos para
 * {@code ContratoTemplateController} (modulo contrato) pois template e responsabilidade
 * do dominio contrato, nao do modulo fundacional config.
 * O modulo config NAO deve depender de modulos de dominio.
 */
@RestController
@RequestMapping("/api/v1/config")
@Tag(name = "Config", description = "Configuracoes globais do sistema")
@RolesAllowed("ADMIN")
public class ConfiguracaoController {

    private final ConfiguracaoService configuracaoService;

    public ConfiguracaoController(ConfiguracaoService configuracaoService) {
        this.configuracaoService = configuracaoService;
    }

    @GetMapping
    @Operation(summary = "Obter configuracoes globais")
    public ResponseEntity<ConfiguracaoResponse> getConfig() {
        var configs = configuracaoService.getConfig();
        return ResponseEntity.ok(ConfiguracaoResponse.of(configs));
    }

    @PutMapping
    @Operation(summary = "Atualizar configuracoes globais")
    public ResponseEntity<Void> atualizar(@RequestBody Map<String, String> valores) {
        configuracaoService.atualizarMultiplos(valores);
        return ResponseEntity.ok().build();
    }
}
