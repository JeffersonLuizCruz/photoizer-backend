package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.service.PagamentoExtraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoint de simulação de pagamento para desenvolvimento local.
 *
 * <p><strong>SEGURANÇA:</strong> este controller substitui o antigo
 * {@code POST /api/v1/ecommerce/galeria/{token}/compras/{id}/simular-pagamento},
 * que era público e permitia a qualquer portador do token da galeria liberar
 * todas as fotos sem pagamento (ver relatório de segurança, achado C1).
 *
 * <p>Mitigações aplicadas:
 * <ul>
 *   <li>{@code @Profile("dev")} — não é carregado em homolog/prod.</li>
 *   <li>{@code @RolesAllowed("ADMIN")} — exige autenticação de staff.</li>
 *   <li>Não aceita o token público da galeria; usa o id do agendamento.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/dev/pagamentos")
@Profile("dev")
@RolesAllowed("ADMIN")
@Tag(name = "Dev Pagamentos", description = "Simulação de pagamento (somente perfil dev)")
public class DevPagamentoController {

    private final PagamentoExtraService pagamentoExtraService;
    private final EcommerceMapper ecommerceMapper;

    public DevPagamentoController(PagamentoExtraService pagamentoExtraService,
                                  EcommerceMapper ecommerceMapper) {
        this.pagamentoExtraService = pagamentoExtraService;
        this.ecommerceMapper = ecommerceMapper;
    }

    @PostMapping("/agendamentos/{agendamentoId}/compras/{compraId}/simular")
    @Operation(summary = "Simular pagamento de uma compra de extras (dev/admin apenas)")
    public ResponseEntity<CompraExtraResponse> simular(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID compraId) {
        var compra = pagamentoExtraService.simularPagamento(agendamentoId, compraId);
        return ResponseEntity.ok(ecommerceMapper.toPublicResponse(compra));
    }
}
