package com.photoizer.crm.ecommerce.event;

import java.util.List;
import java.util.UUID;

/**
 * Publicado quando o staff aprova uma selecao de uma galeria sem edicao.
 * O snapshot fica congelado e as fotos passam a ser entregues em versao
 * original; o modulo foto apenas libera o bloqueio de selecao.
 */
public record SelecaoAprovadaEvent(
    UUID agendamentoId,
    UUID selecaoId,
    List<UUID> fotoIds
) {}
