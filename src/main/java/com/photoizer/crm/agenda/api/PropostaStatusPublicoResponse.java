package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.Agendamento;

import java.time.LocalDateTime;

public record PropostaStatusPublicoResponse(
    String status,
    boolean podeAssinar,
    LocalDateTime dataAssinatura,
    String assinanteNome
) {
    public static PropostaStatusPublicoResponse of(Agendamento a) {
        return new PropostaStatusPublicoResponse(
            a.getStatus().name(),
            false,
            a.getDataAssinatura(),
            a.getAssinanteNome()
        );
    }
}
