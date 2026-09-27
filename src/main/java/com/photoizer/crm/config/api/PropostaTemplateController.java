package com.photoizer.crm.config.api;

import com.photoizer.crm.config.model.ConfigKey;
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
 * Gestão do template do termo de prestação de serviços (usado na assinatura
 * pública da proposta). O template é apenas um valor texto do módulo config.
 */
@RestController
@RequestMapping("/api/v1/proposta/template")
@Tag(name = "Template da Proposta", description = "Gestão do template do termo de prestação de serviços")
@RolesAllowed("ADMIN")
public class PropostaTemplateController {

    private final ConfiguracaoService configuracaoService;

    public PropostaTemplateController(ConfiguracaoService configuracaoService) {
        this.configuracaoService = configuracaoService;
    }

    @GetMapping
    @Operation(summary = "Obter template do termo")
    public ResponseEntity<Map<String, String>> getTemplate() {
        var template = configuracaoService.getValor(ConfigKey.CONTRATO_TEMPLATE);
        return ResponseEntity.ok(Map.of("template", template != null ? template : ""));
    }

    @PutMapping
    @Operation(summary = "Atualizar template do termo")
    public ResponseEntity<Void> atualizarTemplate(@RequestBody Map<String, String> body) {
        var novoTemplate = body.get("template");
        if (novoTemplate != null) {
            configuracaoService.atualizar(ConfigKey.CONTRATO_TEMPLATE, novoTemplate);
        }
        return ResponseEntity.ok().build();
    }

    @PutMapping("/padrao")
    @Operation(summary = "Restaurar template padrão do termo")
    public ResponseEntity<Map<String, String>> restaurarPadrao() {
        var templatePadrao = ConfigKey.CONTRATO_TEMPLATE_PADRAO;
        configuracaoService.atualizar(ConfigKey.CONTRATO_TEMPLATE, templatePadrao);
        return ResponseEntity.ok(Map.of("template", templatePadrao));
    }
}
