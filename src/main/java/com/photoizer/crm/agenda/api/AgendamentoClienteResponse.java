package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.Agendamento;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de resposta para agendamentos do cliente.
 * Movido do módulo cliente para agenda (violação Modulith P1).
 * 
 * Padrão DTO Pattern - isola dados do domínio do contrato da API.
 */
public record AgendamentoClienteResponse(
    UUID id,
    String pacoteNome,
    Integer pacoteQuantidadeFotos,
    Integer pacoteQuantidadeVideos,
    BigDecimal precoFotoExtra,
    LocalDateTime dataHoraEnsaio,
    String status,
    String statusDescricao,
    UUID tokenGaleria,
    String localEnsaio,
    int totalFotosPublicadas,
    int fotosSelecionadasPacote,
    int fotosPagas
) {
    /**
     * Converte entidade Agendamento para DTO de resposta.
     * Inclui descrição legível do status.
     */
    public static AgendamentoClienteResponse of(Agendamento a, int totalFotosPublicadas, int fotosSelecionadasPacote, int fotosPagas) {
        String statusDescricao = switch (a.getStatus().name()) {
            case "AGENDADO" -> "Agendado";
            case "CONFIRMADO" -> "Confirmado";
            case "REALIZADO" -> "Ensaio Realizado";
            case "EM_EDICAO" -> "Fotos em Edição";
            case "SELECAO_ENVIADA" -> "Seleção Enviada";
            case "ENTREGUE" -> "Entregue";
            case "FINALIZADO" -> "Finalizado";
            case "CANCELADO" -> "Cancelado";
            case "NO_SHOW" -> "Não Compareceu";
            case "REAGENDADO" -> "Reagendado";
            default -> a.getStatus().name();
        };
        return new AgendamentoClienteResponse(
            a.getId(),
            a.getPacote().getNome(),
            a.getPacote().getQuantidadeFotos(),
            a.getPacote().getQuantidadeVideos(),
            a.getPacote().getPrecoFotoExtra(),
            a.getDataHoraEnsaio(),
            a.getStatus().name(),
            statusDescricao,
            a.getTokenGaleria(),
            a.getLocalEnsaio(),
            totalFotosPublicadas,
            fotosSelecionadasPacote,
            fotosPagas
        );
    }
}
