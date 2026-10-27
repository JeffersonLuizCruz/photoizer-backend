package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.service.GaleriaCapaService;
import com.photoizer.crm.ecommerce.service.GaleriaEnsaioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/agendamentos/{agendamentoId}/galeria")
@RolesAllowed({"ADMIN", "FOTOGRAFO", "EDITOR"})
@Tag(name = "Admin Galeria", description = "Modo e fase comercial da galeria")
public class AdminGaleriaEnsaioController {

    private final GaleriaEnsaioService galeriaService;
    private final GaleriaCapaService capaService;

    public AdminGaleriaEnsaioController(GaleriaEnsaioService galeriaService,
                                        GaleriaCapaService capaService) {
        this.galeriaService = galeriaService;
        this.capaService = capaService;
    }

    @GetMapping
    @Operation(summary = "Consultar o modo e a fase da galeria")
    public ResponseEntity<GaleriaEnsaioResponse> obter(@PathVariable UUID agendamentoId) {
        return ResponseEntity.ok(toResponse(galeriaService.obter(agendamentoId)));
    }

    @PutMapping("/modo")
    @RolesAllowed({"ADMIN"})
    @Operation(summary = "Alterar o modo da galeria (COM_EDICAO ou SEM_EDICAO; DIRETO foi desativado)")
    public ResponseEntity<GaleriaEnsaioResponse> alterarModo(
            @PathVariable UUID agendamentoId,
            @Valid @RequestBody AlterarModoGaleriaRequest request) {
        return ResponseEntity.ok(toResponse(
            galeriaService.alterarModo(agendamentoId, request.modo())));
    }

    @PutMapping("/capa")
    @RolesAllowed({"ADMIN", "FOTOGRAFO", "EDITOR"})
    @Operation(summary = "Definir ou remover a foto de capa da galeria")
    public ResponseEntity<GaleriaEnsaioResponse> definirCapa(
            @PathVariable UUID agendamentoId,
            @RequestBody DefinirCapaRequest request) {
        var galeria = galeriaService.definirCapa(agendamentoId, request.fotoId());
        if (request.fotoId() != null) {
            capaService.aquecer(agendamentoId, request.fotoId());
        }
        return ResponseEntity.ok(toResponse(galeria));
    }

    @PutMapping("/titulo")
    @RolesAllowed({"ADMIN", "FOTOGRAFO", "EDITOR"})
    @Operation(summary = "Definir ou remover o texto do hero da galeria")
    public ResponseEntity<GaleriaEnsaioResponse> definirTituloHero(
            @PathVariable UUID agendamentoId,
            @Valid @RequestBody AtualizarTituloHeroRequest request) {
        return ResponseEntity.ok(toResponse(
            galeriaService.definirTituloHero(agendamentoId, request.tituloHero())));
    }

    private GaleriaEnsaioResponse toResponse(com.photoizer.crm.ecommerce.model.GaleriaEnsaio galeria) {
        return new GaleriaEnsaioResponse(
            galeria.getAgendamentoId(),
            galeria.getModo().name(),
            galeria.getFase().name(),
            galeria.getSelecaoAtualId(),
            galeria.getCapaFotoId(),
            galeria.getCapaVersao() != null ? galeria.getCapaVersao().name() : null,
            galeria.getTituloHero(),
            galeria.getModo().requerEdicao(),
            galeriaService.podeAlterarModo(galeria.getAgendamentoId()));
    }
}
