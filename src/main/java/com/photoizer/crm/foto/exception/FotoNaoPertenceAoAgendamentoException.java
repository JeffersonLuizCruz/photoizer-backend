package com.photoizer.crm.foto.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class FotoNaoPertenceAoAgendamentoException extends RuntimeException {
    public FotoNaoPertenceAoAgendamentoException(UUID fotoId, UUID agendamentoId) {
        super("Foto " + fotoId + " não pertence ao agendamento " + agendamentoId);
    }
}
