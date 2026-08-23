package com.photoizer.crm.agenda.service;

import com.photoizer.crm.shared.model.TipoRepasse;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
    UUID fotografoId,
    LocalDateTime dataHoraEnsaio,
    LocalDate data,
    String hora,
    Integer duracaoMinutos,
    String localEnsaio,
    String enderecoCompleto,
    BigDecimal taxaDeslocamento,
    BigDecimal custoDeslocamento,
    Boolean repassarDeslocamento,
    MultipartFile comprovanteEntrada,
    Boolean autorizaUsoImagem,
    String clausulasPersonalizadas,
    String observacoes,
    UUID indicadorId,
    String indicadorNome,
    String indicadorTelefone,
    List<FotografoRepasse> fotografos
) {
    public record FotografoRepasse(UUID fotografoId, BigDecimal valorRepassar, TipoRepasse tipoValor, BigDecimal percentual) {
        public FotografoRepasse(UUID fotografoId, BigDecimal valorRepassar) {
            this(fotografoId, valorRepassar, TipoRepasse.FIXO, null);
        }
    }
}
