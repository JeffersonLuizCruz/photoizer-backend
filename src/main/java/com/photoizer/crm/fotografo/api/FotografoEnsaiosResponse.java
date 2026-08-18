package com.photoizer.crm.fotografo.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record FotografoEnsaiosResponse(
    UUID agendamentoId,
    String clienteNome,
    String pacoteNome,
    LocalDateTime dataHoraEnsaio,
    String status,
    BigDecimal valorTotal,
    BigDecimal custosFotografo,
    BigDecimal partilhaFotografo,
    BigDecimal repassarFotografo,
    BigDecimal lucroCrm
) {}