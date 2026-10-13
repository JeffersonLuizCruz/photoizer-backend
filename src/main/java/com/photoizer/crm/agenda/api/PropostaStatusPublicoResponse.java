package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.Agendamento;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "PropostaStatusPublicoResponse", description = "Status da proposta para a página pública.")
public record PropostaStatusPublicoResponse(
    @Schema(description = "Status atual") String status,
    @Schema(description = "Indica se pode confirmar") boolean podeAssinar,
    @Schema(description = "Data da confirmação") LocalDateTime dataAssinatura,
    @Schema(description = "Nome do contratante") String assinanteNome
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
