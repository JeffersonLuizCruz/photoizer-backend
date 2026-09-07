package com.photoizer.crm.notificacao.api;

import com.photoizer.crm.notificacao.service.NotificacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller de notificações.
 *
 * PATTERN: @AuthenticationPrincipal (Spring Security) — refatorado (P1).
 *
 * Antes, o controller aceitava userId como @RequestParam, permitindo que qualquer
 * usuário autenticado ler/editasse notificações de outro (IDOR — DEBT P1 #2).
 *
 * Agora, o userId é extraído diretamente do JWT via @AuthenticationPrincipal,
 * garantindo que cada usuário só acessa suas próprias notificações.
 */
@RestController
@RequestMapping("/api/v1/notificacoes")
@Tag(name = "Notificações", description = "Notificações do sistema para usuários")
public class NotificacaoController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificacaoService notificacaoService;
    private final NotificacaoMapper notificacaoMapper;

    public NotificacaoController(NotificacaoService notificacaoService,
                                 NotificacaoMapper notificacaoMapper) {
        this.notificacaoService = notificacaoService;
        this.notificacaoMapper = notificacaoMapper;
    }

    @GetMapping
    @Operation(summary = "Listar notificações do usuário autenticado (paginado)")
    public ResponseEntity<?> listar(
            @AuthenticationPrincipal String userIdStr,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var userId = UUID.fromString(userIdStr);
        var safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        var pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        var notificacoes = notificacaoService.listar(userId, pageable)
            .map(notificacaoMapper::toResponse);
        return ResponseEntity.ok(notificacoes);
    }

    @GetMapping("/nao-lidas")
    @Operation(summary = "Contar notificações não lidas do usuário autenticado")
    public ResponseEntity<Long> contarNaoLidas(@AuthenticationPrincipal String userIdStr) {
        var userId = UUID.fromString(userIdStr);
        return ResponseEntity.ok(notificacaoService.contarNaoLidas(userId));
    }

    @PatchMapping("/{id}/ler")
    @Operation(summary = "Marcar notificação como lida (valida ownership)")
    public ResponseEntity<Void> marcarComoLida(@PathVariable UUID id,
                                                @AuthenticationPrincipal String userIdStr) {
        var userId = UUID.fromString(userIdStr);
        notificacaoService.marcarComoLida(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/ler-todas")
    @Operation(summary = "Marcar todas as notificações como lidas")
    public ResponseEntity<Void> marcarTodasComoLidas(@AuthenticationPrincipal String userIdStr) {
        var userId = UUID.fromString(userIdStr);
        notificacaoService.marcarTodasComoLidas(userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/limpar")
    @Operation(summary = "Limpar todas as notificações do usuário autenticado")
    public ResponseEntity<Void> limpar(@AuthenticationPrincipal String userIdStr) {
        var userId = UUID.fromString(userIdStr);
        notificacaoService.limpar(userId);
        return ResponseEntity.noContent().build();
    }
}
