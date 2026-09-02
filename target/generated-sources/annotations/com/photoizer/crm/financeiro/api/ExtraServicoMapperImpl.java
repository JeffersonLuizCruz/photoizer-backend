package com.photoizer.crm.financeiro.api;

import com.photoizer.crm.financeiro.model.ExtraServico;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-28T23:12:19-0300",
    comments = "version: 1.6.3, compiler: javac, environment: Java 25.0.4 (Amazon.com Inc.)"
)
@Component
public class ExtraServicoMapperImpl implements ExtraServicoMapper {

    @Override
    public ExtraServicoResponse toResponse(ExtraServico e) {
        if ( e == null ) {
            return null;
        }

        UUID id = null;
        String tipo = null;
        int quantidade = 0;
        BigDecimal valorUnitario = null;
        BigDecimal valorTotal = null;

        id = e.getId();
        if ( e.getTipo() != null ) {
            tipo = e.getTipo().name();
        }
        if ( e.getQuantidade() != null ) {
            quantidade = e.getQuantidade();
        }
        valorUnitario = e.getValorUnitario();
        valorTotal = e.getValorTotal();

        UUID agendamentoId = e.getAgendamento().getId();
        LocalDateTime createdAt = null;

        ExtraServicoResponse extraServicoResponse = new ExtraServicoResponse( id, agendamentoId, tipo, quantidade, valorUnitario, valorTotal, createdAt );

        return extraServicoResponse;
    }
}
