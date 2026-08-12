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
    private final com.photoizer.crm.contrato.service.ContratoTemplateService templateService;

    public ConfiguracaoController(ConfiguracaoService configuracaoService,
                                  com.photoizer.crm.contrato.service.ContratoTemplateService templateService) {
        this.configuracaoService = configuracaoService;
        this.templateService = templateService;
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

    @GetMapping("/contrato/template")
    @Operation(summary = "Obter template do contrato")
    public ResponseEntity<java.util.Map<String, String>> getTemplate() {
        var template = templateService.carregarTemplate();
        return ResponseEntity.ok(java.util.Map.of("template", template != null ? template : ""));
    }

    @PutMapping("/contrato/template")
    @Operation(summary = "Atualizar template do contrato")
    public ResponseEntity<Void> atualizarTemplate(@RequestBody java.util.Map<String, String> body) {
        var novoTemplate = body.get("template");
        if (novoTemplate != null) {
            configuracaoService.atualizarValorTexto(templateService.getTemplateKey(), novoTemplate);
        }
        return ResponseEntity.ok().build();
    }

    @PutMapping("/contrato/template/padrao")
    @Operation(summary = "Restaurar template padrão do contrato")
    public ResponseEntity<java.util.Map<String, String>> restaurarPadrao() {
        configuracaoService.atualizarValorTexto(templateService.getTemplateKey(), com.photoizer.crm.shared.config.DataSeeder.TEMPLATO_PADRAO);
        return ResponseEntity.ok(java.util.Map.of("template", com.photoizer.crm.shared.config.DataSeeder.TEMPLATO_PADRAO));
    }
}
