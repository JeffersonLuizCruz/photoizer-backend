package com.photoizer.crm.notificacao.api;

import com.photoizer.crm.notificacao.service.NotificacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notificacoes")
@Tag(name = "Notificações", description = "Notificações do sistema para usuários")
public class NotificacaoController {

    private final NotificacaoService notificacaoService;

    public NotificacaoController(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    @GetMapping
    @Operation(summary = "Listar notificações de um usuário")
    public ResponseEntity<List<NotificacaoResponse>> listar(@RequestParam UUID userId) {
        var notificacoes = notificacaoService.listar(userId).stream()
            .map(NotificacaoResponse::of)
            .toList();
        return ResponseEntity.ok(notificacoes);
    }

    @GetMapping("/nao-lidas")
    @Operation(summary = "Contar notificações não lidas de um usuário")
    public ResponseEntity<Long> contarNaoLidas(@RequestParam UUID userId) {
        return ResponseEntity.ok(notificacaoService.contarNaoLidas(userId));
    }

    @PatchMapping("/{id}/ler")
    @Operation(summary = "Marcar notificação como lida")
    public ResponseEntity<Void> marcarComoLida(@PathVariable UUID id) {
        notificacaoService.marcarComoLida(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/ler-todas")
    @Operation(summary = "Marcar todas as notificações como lidas")
    public ResponseEntity<Void> marcarTodasComoLidas(@RequestParam UUID userId) {
        notificacaoService.marcarTodasComoLidas(userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/limpar")
    @Operation(summary = "Limpar todas as notificações de um usuário")
    public ResponseEntity<Void> limpar(@RequestParam UUID userId) {
        notificacaoService.limpar(userId);
        return ResponseEntity.noContent().build();
    }
}