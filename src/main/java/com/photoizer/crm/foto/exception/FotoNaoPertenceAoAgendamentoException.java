package com.photoizer.crm.foto.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.ForbiddenException;

import java.util.UUID;

public class FotoNaoPertenceAoAgendamentoException extends ForbiddenException {
    public FotoNaoPertenceAoAgendamentoException(UUID fotoId, UUID agendamentoId) {
        super(ErrorCode.FOTO_NAO_PERTENCE_AO_AGENDAMENTO, "Foto " + fotoId + " não pertence ao agendamento " + agendamentoId);
    }
}
