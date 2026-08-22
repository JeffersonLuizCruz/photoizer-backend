package com.photoizer.crm.ecommerce.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record TokenGaleriaRegeneradoEvent(
    UUID agendamentoId,
    UUID novoToken,
    LocalDateTime expiracao
) {}
