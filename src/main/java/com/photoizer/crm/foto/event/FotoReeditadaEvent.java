package com.photoizer.crm.foto.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Publicado quando o staff envia uma nova edição para uma foto que já tinha
 * sido entregue ao cliente. Diferente de {@link FotoEditadaEvent}, que marca a
 * primeira edição dentro de uma seleção ativa.
 */
public record FotoReeditadaEvent(
    UUID agendamentoId,
    UUID fotoId,
    LocalDateTime editadaEm
) {}
