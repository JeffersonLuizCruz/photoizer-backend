package com.photoizer.crm.notificacao.service;

import com.photoizer.crm.notificacao.exception.NotificacaoNaoEncontradaException;
import com.photoizer.crm.notificacao.exception.NotificacaoNaoPertenceAoUsuarioException;
import com.photoizer.crm.notificacao.model.Notificacao;
import com.photoizer.crm.notificacao.model.TipoNotificacao;
import com.photoizer.crm.notificacao.repository.NotificacaoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service de notificações.
 *
 * PATTERN: Transactional Script — métodos de escrita com @Transactional explícito,
 * leituras com @Transactional(readOnly = true) para otimização do Hibernate.
 */
@Service
public class NotificacaoService {

    private final NotificacaoRepository repository;

    public NotificacaoService(NotificacaoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Notificacao criar(UUID userId, String titulo, String mensagem, String link, TipoNotificacao tipo) {
        return repository.save(new Notificacao(userId, titulo, mensagem, link, tipo));
    }

    @Transactional(readOnly = true)
    public Page<Notificacao> listar(UUID userId, Pageable pageable) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long contarNaoLidas(UUID userId) {
        return repository.countByUserIdAndLidaFalse(userId);
    }

    /**
     * Marca uma notificação como lida com validação de ownership.
     * P1: garante que o usuário autenticado só manipula suas próprias notificações.
     */
    @Transactional
    public void marcarComoLida(UUID id, UUID userId) {
        var notificacao = repository.findById(id)
            .orElseThrow(() -> new NotificacaoNaoEncontradaException(id));
        if (!notificacao.getUserId().equals(userId)) {
            throw new NotificacaoNaoPertenceAoUsuarioException();
        }
        notificacao.setLida(true);
        repository.save(notificacao);
    }

    /**
     * Marca todas as notificações como lidas via query bulk (1 UPDATE, não N+1).
     */
    @Transactional
    public void marcarTodasComoLidas(UUID userId) {
        repository.marcarTodasComoLidas(userId);
    }

    /**
     * Remove todas as notificações do usuário via query derivada (1 DELETE, não carrega em memória).
     */
    @Transactional
    public void limpar(UUID userId) {
        repository.deleteByUserId(userId);
    }
}
