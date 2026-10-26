package com.photoizer.crm.config.api;

import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.config.service.LogoContratadaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    private final LogoContratadaService logoContratadaService;

    public ConfiguracaoController(ConfiguracaoService configuracaoService,
                                  LogoContratadaService logoContratadaService) {
        this.configuracaoService = configuracaoService;
        this.logoContratadaService = logoContratadaService;
    }

    @GetMapping
    @Operation(summary = "Obter configuracoes globais")
    public ResponseEntity<Map<String, String>> getConfig() {
        return ResponseEntity.ok(configuracaoService.getConfig());
    }

    @PutMapping
    @Operation(summary = "Atualizar configuracoes globais")
    public ResponseEntity<Void> atualizar(@RequestBody Map<String, String> valores) {
        configuracaoService.atualizarMultiplos(valores);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Enviar logo da contratada exibida na proposta pública")
    public ResponseEntity<Map<String, String>> enviarLogo(
            @RequestPart("arquivo") MultipartFile arquivo) {
        var caminho = logoContratadaService.salvar(arquivo);
        return ResponseEntity.ok(Map.of("logoContratada", caminho));
    }

    @DeleteMapping("/logo")
    @Operation(summary = "Remover logo personalizada e voltar à arte padrão")
    public ResponseEntity<Void> removerLogo() {
        logoContratadaService.remover();
        return ResponseEntity.noContent().build();
    }
}
