package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.Avaliacao;
import com.photoizer.crm.ecommerce.model.CompraExtra;
import com.photoizer.crm.ecommerce.model.FotoComentario;
import com.photoizer.crm.ecommerce.model.Sessao;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * PATTERN: MapStruct Mapper
 * Substitui os métodos estáticos `static of()` manuais nos records DTO.
 * Motivo: eliminar boilerplate de mapeamento, garantir type-safety em compile-time,
 * e manter consistência com os mappers já criados nos módulos agenda, despesa e edicao.
 *
 * Cada método de mapping controla os campos expostos na resposta.
 * Para CompraExtra, existem 3 variantes (of, ofPublic, ofAdmin) que diferem
 * na exposição da URL do comprovante.
 */
@Mapper(componentModel = "spring")
public interface EcommerceMapper {

    // ==================== CompraExtra ====================

    /**
     * Mapeamento completo (admin interno). Expõe o caminho bruto do comprovante.
     * Uso: contexto administrativo onde o path do filesystem é necessário.
     */
    @Mapping(target = "status", expression = "java(c.getStatus().name())")
    @Mapping(target = "metodoPagamento", expression = "java(c.getMetodoPagamento() != null ? c.getMetodoPagamento().name() : null)")
    CompraExtraResponse toResponse(CompraExtra c);

    /**
     * Mapeamento seguro para respostas públicas (galeria do cliente).
     * Oculta o caminho absoluto do comprovante no filesystem do servidor.
     */
    @Mapping(target = "status", expression = "java(c.getStatus().name())")
    @Mapping(target = "metodoPagamento", expression = "java(c.getMetodoPagamento() != null ? c.getMetodoPagamento().name() : null)")
    @Mapping(target = "urlComprovante", constant = "null")
    CompraExtraResponse toPublicResponse(CompraExtra c);

    /**
     * Mapeamento para respostas administrativas.
     * Expõe apenas a URL autenticada do comprovante, nunca o caminho do filesystem.
     */
    @Mapping(target = "status", expression = "java(c.getStatus().name())")
    @Mapping(target = "metodoPagamento", expression = "java(c.getMetodoPagamento() != null ? c.getMetodoPagamento().name() : null)")
    @Mapping(target = "urlComprovante", expression = "java(c.getUrlComprovante() != null ? \"/api/v1/admin/ecommerce/compras/\" + c.getId() + \"/comprovante\" : null)")
    CompraExtraResponse toAdminResponse(CompraExtra c);

    // ==================== FotoComentario ====================

    @Mapping(target = "origem", expression = "java(c.getOrigem().name())")
    @Mapping(target = "createdAt", expression = "java(c.getAuditInfo().getCreatedAt())")
    ComentarioResponse toComentarioResponse(FotoComentario c);

    // ==================== Avaliacao ====================

    @Mapping(target = "createdAt", expression = "java(a.getAuditInfo().getCreatedAt())")
    AvaliacaoResponse toAvaliacaoResponse(Avaliacao a);

    // ==================== Sessao ====================

    @Mapping(target = "createdAt", expression = "java(s.getAuditInfo().getCreatedAt())")
    SessaoResponse toSessaoResponse(Sessao s);
}
