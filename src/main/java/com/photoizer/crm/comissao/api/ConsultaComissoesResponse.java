package com.photoizer.crm.comissao.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response do endpoint GET /comissoes/consulta?telefone=.
 * Substitui o Map<String,Object> por record tipado com compile-time safety.
 */
public record ConsultaComissoesResponse(
    String indicadorNome,
    String indicadorTelefone,
    BigDecimal totalPendente,
    BigDecimal totalPago,
    List<IndicacaoResponse> indicacoes
) {}
