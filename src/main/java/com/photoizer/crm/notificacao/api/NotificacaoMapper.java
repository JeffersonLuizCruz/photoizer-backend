package com.photoizer.crm.notificacao.api;

import com.photoizer.crm.notificacao.model.Notificacao;
import org.mapstruct.Mapper;

/**
 * MapStruct Mapper para DTOs do módulo notificação.
 *
 * PATTERN: Mapper Pattern (MapStruct)
 *
 * Motivo: Substitui o método manual `static of()` no record NotificacaoResponse.
 * MapStruct gera implementação em compile-time, eliminando:
 * - Bugs de mapeamento (campos esquecidos ou mapeados incorretamente)
 * - Código boilerplate
 * - Inconsistências entre mapeamentos
 *
 * Segue a decisão aprovada no DEBT.md §4.6 e o padrão já adotado
 * nos módulos agenda (AgendamentoMapper), despesa (DespesaMapper),
 * edicao (EdicaoMapper) e ecommerce (EcommerceMapper).
 *
 * A interface gera um bean Spring (componentModel = "spring") que
 * pode ser injetado em controllers e services.
 */
@Mapper(componentModel = "spring")
public interface NotificacaoMapper {

    NotificacaoResponse toResponse(Notificacao notificacao);
}
