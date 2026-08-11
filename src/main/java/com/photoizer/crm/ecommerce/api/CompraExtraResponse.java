package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.CompraExtra;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CompraExtraResponse(
    UUID id,
    UUID agendamentoId,
    BigDecimal valorTotal,
    String status,
    String urlComprovante,
    LocalDateTime dataPagamento,
    Integer quantidadeFotos,
    String metodoPagamento
) {
    public static CompraExtraResponse of(CompraExtra c) {
        return new CompraExtraResponse(
            c.getId(), c.getAgendamentoId(), c.getValorTotal(),
            c.getStatus().name(), c.getUrlComprovante(), c.getDataPagamento(),
            c.getQuantidadeFotos(),
            c.getMetodoPagamento() != null ? c.getMetodoPagamento().name() : null
        );
    }

    /**
     * Mapeamento seguro para respostas públicas (galeria do cliente).
     * Oculta o caminho absoluto do comprovante no filesystem do servidor.
     */
    public static CompraExtraResponse ofPublic(CompraExtra c) {
        return new CompraExtraResponse(
            c.getId(), c.getAgendamentoId(), c.getValorTotal(),
            c.getStatus().name(), null, c.getDataPagamento(),
            c.getQuantidadeFotos(),
            c.getMetodoPagamento() != null ? c.getMetodoPagamento().name() : null
        );
    }

    /**
     * Mapeamento para respostas administrativas.
     * Expõe apenas a URL autenticada do comprovante, nunca o caminho do filesystem.
     */
    public static CompraExtraResponse ofAdmin(CompraExtra c) {
        var comprovanteUrl = c.getUrlComprovante() != null
            ? "/api/v1/admin/ecommerce/compras/" + c.getId() + "/comprovante"
            : null;
        return new CompraExtraResponse(
            c.getId(), c.getAgendamentoId(), c.getValorTotal(),
            c.getStatus().name(), comprovanteUrl, c.getDataPagamento(),
            c.getQuantidadeFotos(),
            c.getMetodoPagamento() != null ? c.getMetodoPagamento().name() : null
        );
    }
}
