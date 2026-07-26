package com.photoizer.crm.notificacao.api;

import com.photoizer.crm.notificacao.model.Notificacao;
import com.photoizer.crm.notificacao.service.NotificacaoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notificacoes")
public class NotificacaoController {

    private final NotificacaoService notificacaoService;

    public NotificacaoController(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    @GetMapping
    public ResponseEntity<?> listar(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(notificacaoService.listar(UUID.fromString(userId)));
    }

    @GetMapping("/nao-lidas")
    public ResponseEntity<?> countNaoLidas(@AuthenticationPrincipal String userId) {
        var count = notificacaoService.countNaoLidas(UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PutMapping("/{id}/ler")
    public ResponseEntity<Void> marcarComoLida(@PathVariable UUID id) {
        notificacaoService.marcarComoLida(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/ler-todas")
    public ResponseEntity<Void> marcarTodasComoLidas(@AuthenticationPrincipal String userId) {
        notificacaoService.marcarTodasComoLidas(UUID.fromString(userId));
        return ResponseEntity.ok().build();
    }
}
