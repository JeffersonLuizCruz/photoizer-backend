package com.photoizer.crm.agenda.service;

import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CriarAgendamentoCommand(
    UUID clienteId,
    String nome,
    String telefone,
    String email,
    String cpf,
    String cidade,
    String estado,
    String origem,
    UUID pacoteId,
    UUID editorId,
    LocalDateTime dataHoraEnsaio,
    LocalDate data,
    String hora,
    Integer duracaoMinutos,
    String localEnsaio,
    String enderecoCompleto,
    BigDecimal taxaDeslocamento,
    MultipartFile comprovanteEntrada,
    Boolean autorizaUsoImagem,
    String clausulasPersonalizadas,
    String observacoes,
    String indicadorNome,
    String indicadorTelefone
) {
}
