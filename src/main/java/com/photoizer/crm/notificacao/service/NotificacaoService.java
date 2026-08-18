package com.photoizer.crm.notificacao.service;

import com.photoizer.crm.notificacao.model.Notificacao;
import com.photoizer.crm.notificacao.model.TipoNotificacao;
import com.photoizer.crm.notificacao.repository.NotificacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class NotificacaoService {

    private final NotificacaoRepository repository;

    public NotificacaoService(NotificacaoRepository repository) {
        this.repository = repository;
    }

    public Notificacao criar(UUID userId, String titulo, String mensagem, String link, TipoNotificacao tipo) {
        return repository.save(new Notificacao(userId, titulo, mensagem, link, tipo));
    }

    @Transactional(readOnly = true)
    public List<Notificacao> listar(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long contarNaoLidas(UUID userId) {
        return repository.countByUserIdAndLidaFalse(userId);
    }

    public void marcarComoLida(UUID id) {
        var notificacao = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Notificação não encontrada: " + id));
        notificacao.setLida(true);
        repository.save(notificacao);
    }

    public void marcarTodasComoLidas(UUID userId) {
        var notificacoes = repository.findByUserIdOrderByCreatedAtDesc(userId);
        for (var n : notificacoes) {
            if (!n.isLida()) {
                n.setLida(true);
                repository.save(n);
            }
        }
    }

    public void limpar(UUID userId) {
        var notificacoes = repository.findByUserIdOrderByCreatedAtDesc(userId);
        repository.deleteAll(notificacoes);
    }
}