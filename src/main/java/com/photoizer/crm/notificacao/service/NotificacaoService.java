package com.photoizer.crm.notificacao.service;

import com.photoizer.crm.notificacao.model.Notificacao;
import com.photoizer.crm.notificacao.repository.NotificacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class NotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoService.class);

    private final NotificacaoRepository notificacaoRepository;

    public NotificacaoService(NotificacaoRepository notificacaoRepository) {
        this.notificacaoRepository = notificacaoRepository;
    }

    public void criar(UUID userId, String titulo, String mensagem, String link) {
        var notificacao = new Notificacao(userId, titulo, mensagem, link);
        notificacaoRepository.save(notificacao);
        log.info("Notificacao criada para usuario {}: {}", userId, titulo);
    }

    @Transactional(readOnly = true)
    public List<Notificacao> listar(UUID userId) {
        return notificacaoRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long countNaoLidas(UUID userId) {
        return notificacaoRepository.countByUserIdAndLidaFalse(userId);
    }

    public void marcarComoLida(UUID id) {
        notificacaoRepository.findById(id).ifPresent(n -> {
            n.setLida(true);
            notificacaoRepository.save(n);
        });
    }

    public void marcarTodasComoLidas(UUID userId) {
        var notificacoes = notificacaoRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (var n : notificacoes) {
            n.setLida(true);
            notificacaoRepository.save(n);
        }
    }

    public void enviarLembrete(String destinatario, String mensagem) {
        log.info("Enviando lembrete para {}: {}", destinatario, mensagem);
    }

    public void enviarAlerta(String destinatario, String mensagem) {
        log.info("Enviando alerta para {}: {}", destinatario, mensagem);
    }

    public void notificarNovaCompraExtra(UUID agendamentoId, UUID compraExtraId, BigDecimal valorTotal, int quantidade) {
        log.info("[NOTIFICACAO ADMIN] Nova compra de extras! Agendamento: {}, Valor: R$ {}, Fotos: {}",
            agendamentoId, valorTotal, quantidade);
    }

    public void notificarCompraExtraConfirmada(UUID agendamentoId, UUID compraExtraId, BigDecimal valorTotal) {
        log.info("[NOTIFICACAO ADMIN] Compra de extras confirmada! Agendamento: {}, Valor: R$ {}",
            agendamentoId, valorTotal);
    }
}
