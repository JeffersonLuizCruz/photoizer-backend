package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import org.mapstruct.Mapper;

import java.time.format.DateTimeFormatter;

/**
 * Mapper MapStruct para conversão de AgendamentoFotografo para RepasseResponse.
 *
 * Design Pattern: Data Transfer Object (DTO) + Mapper Pattern.
 * Motivo: eliminar retorno de entidade JPA na API (violação P1 do DEBT.md),
 * padronizar conversão com MapStruct (consistente com 7 módulos do projeto).
 *
 * A estrutura do RepasseResponse é compatível com a interface
 * AgendamentoFotografo do frontend TypeScript para manter backward compatibility.
 */
@Mapper(componentModel = "spring")
public abstract class RepasseMapper {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Implementação manual do mapeamento para garantir compatibilidade
     * com a estrutura aninhada que o frontend espera.
     */
    public RepasseResponse toCompatibleResponse(AgendamentoFotografo af) {
        if (af == null) return null;

        var agendamento = af.getAgendamento();
        var fotografo = af.getFotografo();

        RepasseResponse.AgendamentoResumo agendamentoResumo = null;
        if (agendamento != null) {
            RepasseResponse.ClienteResumo clienteResumo = null;
            if (agendamento.getCliente() != null) {
                clienteResumo = new RepasseResponse.ClienteResumo(
                    agendamento.getCliente().getId(),
                    agendamento.getCliente().getNome()
                );
            }

            RepasseResponse.PacoteResumo pacoteResumo = null;
            if (agendamento.getPacote() != null) {
                pacoteResumo = new RepasseResponse.PacoteResumo(
                    agendamento.getPacote().getId(),
                    agendamento.getPacote().getNome()
                );
            }

            agendamentoResumo = new RepasseResponse.AgendamentoResumo(
                agendamento.getId(),
                clienteResumo,
                pacoteResumo,
                agendamento.getDataHoraEnsaio(),
                agendamento.getStatus() != null ? agendamento.getStatus().name() : null
            );
        }

        RepasseResponse.FotografoResumo fotografoResumo = null;
        if (fotografo != null) {
            fotografoResumo = new RepasseResponse.FotografoResumo(
                fotografo.getId(),
                fotografo.getNome(),
                fotografo.getEmail(),
                fotografo.getTelefone(),
                fotografo.getPapel() != null ? fotografo.getPapel().name() : null,
                fotografo.isAtivo()
            );
        }

        RepasseResponse.AuditInfoResponse auditInfo = null;
        if (af.getAuditInfo() != null) {
            auditInfo = new RepasseResponse.AuditInfoResponse(
                af.getAuditInfo().getCreatedAt() != null ? af.getAuditInfo().getCreatedAt().format(DATE_FORMAT) : null,
                af.getAuditInfo().getUpdatedAt() != null ? af.getAuditInfo().getUpdatedAt().format(DATE_FORMAT) : null
            );
        }

        return new RepasseResponse(
            af.getId(),
            auditInfo,
            agendamentoResumo,
            fotografoResumo,
            af.getValorRepassar(),
            af.getStatus(),
            af.getTipoValor() != null ? af.getTipoValor().name() : null,
            af.getPercentual(),
            af.getPapelParceiro() != null ? af.getPapelParceiro().name() : null,
            af.getDataPagamento()
        );
    }
}
