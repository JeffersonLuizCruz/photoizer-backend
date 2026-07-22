package com.photoizer.crm.config.api;

import com.photoizer.crm.config.service.ConfiguracaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
@Tag(name = "Config", description = "Configurações globais do sistema")
public class ConfiguracaoController {

    private final ConfiguracaoService configuracaoService;

    public ConfiguracaoController(ConfiguracaoService configuracaoService) {
        this.configuracaoService = configuracaoService;
    }

    @GetMapping
    @Operation(summary = "Obter configurações globais")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(configuracaoService.getConfig());
    }

    @PutMapping
    @Operation(summary = "Atualizar configurações globais")
    public ResponseEntity<Void> atualizar(@RequestBody Map<String, String> valores) {
        configuracaoService.atualizarMultiplos(valores);
        return ResponseEntity.ok().build();
    }
}
