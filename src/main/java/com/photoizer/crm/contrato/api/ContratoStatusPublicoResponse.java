package com.photoizer.crm.contrato.api;

import com.photoizer.crm.contrato.model.Contrato;
import com.photoizer.crm.contrato.model.StatusContrato;

import java.time.LocalDateTime;

public record ContratoStatusPublicoResponse(
    StatusContrato status,
    boolean podeAssinar,
    String motivoDevolucao,
    LocalDateTime dataAssinatura,
    String assinanteNome
) {
    public static ContratoStatusPublicoResponse of(Contrato c) {
        return new ContratoStatusPublicoResponse(
            c.getStatus(),
            c.getStatus() == StatusContrato.PUBLICADO || c.getStatus() == StatusContrato.DEVOLVIDO,
            c.getMotivoDevolucao(),
            c.getDataAssinatura(),
            c.getClienteNome()
        );
    }
}