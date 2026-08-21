package com.photoizer.crm.contrato.api;

import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.contrato.service.ContratoTemplateService;
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
 * Controller responsavel pela gestao do template de contrato.
 *
 * <p>Pattern: Boundary Controller — este controller pertence ao modulo CONTRATO,
 * nao ao modulo CONFIG. O template e uma responsabilidade do dominio contrato.
 * O modulo config apenas armazena o valor como texto generico.
 *
 * <p>Movido de ConfiguracaoController para respeitar o isolamento do Modulith:
 * modulo fundacional (config) nao pode depender de modulo de dominio (contrato).
 */
@RestController
@RequestMapping("/api/v1/contrato/template")
@Tag(name = "Contrato Template", description = "Gestao do template de contrato")
@RolesAllowed("ADMIN")
public class ContratoTemplateController {

    private final ContratoTemplateService templateService;
    private final ConfiguracaoService configuracaoService;

    public ContratoTemplateController(ContratoTemplateService templateService,
                                      ConfiguracaoService configuracaoService) {
        this.templateService = templateService;
        this.configuracaoService = configuracaoService;
    }

    @GetMapping
    @Operation(summary = "Obter template do contrato")
    public ResponseEntity<Map<String, String>> getTemplate() {
        var template = templateService.carregarTemplate();
        return ResponseEntity.ok(Map.of("template", template != null ? template : ""));
    }

    @PutMapping
    @Operation(summary = "Atualizar template do contrato")
    public ResponseEntity<Void> atualizarTemplate(@RequestBody Map<String, String> body) {
        var novoTemplate = body.get("template");
        if (novoTemplate != null) {
            configuracaoService.atualizar(ConfigKey.CONTRATO_TEMPLATE, novoTemplate);
        }
        return ResponseEntity.ok().build();
    }

    @PutMapping("/padrao")
    @Operation(summary = "Restaurar template padrao do contrato")
    public ResponseEntity<Map<String, String>> restaurarPadrao() {
        var templatePadrao = ContratoTemplateService.TEMPLATE_PADRAO;
        configuracaoService.atualizar(ConfigKey.CONTRATO_TEMPLATE, templatePadrao);
        return ResponseEntity.ok(Map.of("template", templatePadrao));
    }
}
