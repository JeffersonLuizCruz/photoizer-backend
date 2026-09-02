package com.photoizer.crm.indicador.service;

import java.math.BigDecimal;

/**
 * Command record para operações de criação/atualização de Indicador.
 *
 * Pattern: Command Object (Clean Architecture) — substitui parâmetros posicionais
 * por um record imutável, eliminando confusão de ordem de argumentos e
 * facilitando a evolução do método (adicionar campos não quebra assinatura).
 *
 * Nota: Validação (@NotBlank, @DecimalMin, etc) é feita no IndicadorRequest
 * via @Valid no controller. Este record é construído apenas a partir de um
 * request já validado.
 */
public record IndicadorCommand(
    String nome,
    String telefone,
    String observacoes,
    BigDecimal percentualComissao
) {}
