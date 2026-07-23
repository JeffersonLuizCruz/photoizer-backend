package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import com.photoizer.crm.ecommerce.service.EcommerceService;
import com.photoizer.crm.foto.api.FotoEnsaioResponse;
import com.photoizer.crm.foto.model.StatusFoto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/agendamentos/{agendamentoId}/ecommerce")
@Tag(name = "Admin Ecommerce", description = "Gestão administrativa do ecommerce de fotos")
public class AdminEcommerceController {

    private final EcommerceService ecommerceService;
    private final AgendamentoRepository agendamentoRepository;

    public AdminEcommerceController(EcommerceService ecommerceService,
                                    AgendamentoRepository agendamentoRepository) {
        this.ecommerceService = ecommerceService;
        this.agendamentoRepository = agendamentoRepository;
    }

    @GetMapping
    @Operation(summary = "Resumo completo do ecommerce do agendamento")
    public ResponseEntity<AdminEcommerceResumoResponse> resumo(@PathVariable UUID agendamentoId) {
        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new RuntimeException("Agendamento não encontrado"));

        var fotos = ecommerceService.listarFotosPorAgendamento(agendamentoId);
        var compras = ecommerceService.listarComprasPorAgendamento(agendamentoId);

        var totalFotos = fotos.size();
        var publicadas = (int) fotos.stream().filter(f -> f.getStatus() == StatusFoto.PUBLICADA).count();
        var selecionadas = (int) fotos.stream().filter(f -> f.isSelecionadaPacote()).count();
        var pagas = (int) fotos.stream().filter(f -> f.getStatus() == StatusFoto.PAGA).count();
        var aguardando = (int) fotos.stream()
            .filter(f -> f.getStatus() == StatusFoto.AGUARDANDO_COMPROVANTE || f.getStatus() == StatusFoto.AGUARDANDO_CONFIRMACAO)
            .count();

        var valorTotalExtras = compras.stream()
            .filter(c -> c.getStatus() == StatusCompraExtra.PAGA)
            .map(c -> c.getValorTotal())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var fotosResponse = fotos.stream().map(FotoEnsaioResponse::of).toList();
        var comprasResponse = compras.stream().map(CompraExtraResponse::of).toList();
        var linkGaleria = "/g/" + agendamento.getTokenGaleria();

        return ResponseEntity.ok(new AdminEcommerceResumoResponse(
            totalFotos, publicadas, selecionadas, pagas, aguardando,
            fotosResponse, comprasResponse, valorTotalExtras, linkGaleria,
            agendamento.getTokenGaleria()
        ));
    }

    @PatchMapping("/fotos/{fotoId}/selecao")
    @Operation(summary = "Override admin de seleção de foto no pacote")
    public ResponseEntity<FotoEnsaioResponse> overrideSelecao(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID fotoId,
            @RequestParam boolean selecionada) {
        var foto = ecommerceService.overrideSelecao(agendamentoId, fotoId, selecionada);
        return ResponseEntity.ok(FotoEnsaioResponse.of(foto));
    }

    @PostMapping("/regen-token")
    @Operation(summary = "Regenerar token de acesso da galeria")
    public ResponseEntity<Void> regerarToken(@PathVariable UUID agendamentoId) {
        ecommerceService.regerarToken(agendamentoId);
        return ResponseEntity.ok().build();
    }
}
