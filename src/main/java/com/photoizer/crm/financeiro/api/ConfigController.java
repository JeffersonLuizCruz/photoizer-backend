package com.photoizer.crm.financeiro.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
@Tag(name = "Config", description = "Configuracoes globais do sistema")
public class ConfigController {

    @GetMapping
    @Operation(summary = "Obter configuracoes globais")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(Map.of(
            "valorUnitarioFotoExtra", BigDecimal.valueOf(15)
        ));
    }
}
