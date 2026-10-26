package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.service.ConfiguracaoPagamentoEcommerceService;
import com.photoizer.crm.shared.config.openapi.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ecommerce/pagamento")
@RolesAllowed({"ADMIN"})
@Tag(name = OpenApiTags.ADMIN_ECOMMERCE, description = "Gestão administrativa do e-commerce de fotos.")
public class AdminPagamentoEcommerceController {

    private final ConfiguracaoPagamentoEcommerceService configuracaoPagamentoService;

    public AdminPagamentoEcommerceController(
            ConfiguracaoPagamentoEcommerceService configuracaoPagamentoService) {
        this.configuracaoPagamentoService = configuracaoPagamentoService;
    }

    @GetMapping
    @Operation(operationId = "obterConfiguracaoPagamentoEcommerce",
        summary = "Obter a configuração de PIX do e-commerce (ADMIN)")
    public ResponseEntity<ConfiguracaoPagamentoResponse> obter() {
        var configuracao = configuracaoPagamentoService.obter();
        return ResponseEntity.ok(new ConfiguracaoPagamentoResponse(
            configuracao.getPixChave(), configuracao.getPixTipoChave()));
    }

    @PutMapping
    @Operation(operationId = "atualizarConfiguracaoPagamentoEcommerce",
        summary = "Atualizar a configuração de PIX do e-commerce (ADMIN)",
        description = "Somente ADMIN. A alteração reflete imediatamente em todas as galerias.")
    public ResponseEntity<ConfiguracaoPagamentoResponse> atualizar(
            @Valid @RequestBody AtualizarConfiguracaoPagamentoRequest request) {
        var configuracao = configuracaoPagamentoService.atualizar(
            request.pixChave(), request.pixTipoChave());
        return ResponseEntity.ok(new ConfiguracaoPagamentoResponse(
            configuracao.getPixChave(), configuracao.getPixTipoChave()));
    }
}
