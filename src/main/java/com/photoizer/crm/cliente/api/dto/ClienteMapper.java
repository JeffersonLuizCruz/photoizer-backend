package com.photoizer.crm.cliente.api.dto;

import com.photoizer.crm.cliente.api.AtualizarPerfilRequest;
import com.photoizer.crm.cliente.model.Cliente;
import org.springframework.stereotype.Component;

/**
 * Mapper para conversão entre entidade Cliente e DTOs.
 * Centraliza a lógica de mapeamento e evita duplicação.
 * 
 * NOTA: Quando MapStruct for configurado no projeto, este mapper pode ser
 * substituído por uma interface MapStruct com @Mapper(componentModel = "spring").
 * Mantemos implementação manual暂时 para manter compatibilidade sem configuração adicional.
 */
@Component
public class ClienteMapper {

    /**
     * Converte entidade Cliente para DTO de resposta (sem senhaHash).
     * Padrão DTO Pattern - isola dados sensíveis do contrato da API.
     */
    public ClienteResponse toResponse(Cliente cliente) {
        if (cliente == null) return null;
        return new ClienteResponse(
            cliente.getId(),
            cliente.getNome(),
            cliente.getTelefone(),
            cliente.getEmail(),
            cliente.getCpf(),
            cliente.getCidade(),
            cliente.getEstado(),
            cliente.getOrigem(),
            cliente.getObservacoes(),
            cliente.getDataCadastro(),
            cliente.getPreferencias()
        );
    }

    /**
     * Converte entidade Cliente para DTO de resposta admin.
     * Inclui campos de auditoria (createdAt, updatedAt).
     */
    public ClienteAdminResponse toAdminResponse(Cliente cliente) {
        if (cliente == null) return null;
        return new ClienteAdminResponse(
            cliente.getId(),
            cliente.getNome(),
            cliente.getTelefone(),
            cliente.getEmail(),
            cliente.getCpf(),
            cliente.getCidade(),
            cliente.getEstado(),
            cliente.getOrigem(),
            cliente.getObservacoes(),
            cliente.getDataCadastro(),
            cliente.getPreferencias(),
            cliente.getAuditInfo().getCreatedAt(),
            cliente.getAuditInfo().getUpdatedAt()
        );
    }

    /**
     * Converte DTO de criação para entidade Cliente.
     * Define dataCadastro automaticamente (corrige inconsistência P2).
     */
    public Cliente toEntity(CriarClienteRequest request) {
        if (request == null) return null;
        return Cliente.builder()
            .nome(request.nome())
            .telefone(request.telefone())
            .email(request.email())
            .cpf(request.cpf())
            .cidade(request.cidade())
            .estado(request.estado())
            .origem(request.origem())
            .observacoes(request.observacoes())
            .preferencias(request.preferencias())
            .build();
    }

    /**
     * Atualiza entidade existente com dados do DTO.
     * Usa método de domínio `atualizarDados` em vez de setters diretos.
     * Padrão Domain Model - encapsula regras de negócio na entidade.
     */
    public void updateEntity(Cliente cliente, AtualizarClienteRequest request) {
        if (cliente == null || request == null) return;
        cliente.atualizarDados(
            request.nome(),
            request.telefone(),
            request.email(),
            request.cpf(),
            request.cidade(),
            request.estado(),
            request.origem(),
            request.observacoes(),
            request.preferencias()
        );
    }

    /**
     * Atualiza perfil do cliente (dados limitados).
     * Usa método de domínio `atualizarPerfil` em vez de setters diretos.
     * Padrão Domain Model - encapsula regras de negócio na entidade.
     */
    public void updatePerfil(Cliente cliente, AtualizarPerfilRequest request) {
        if (cliente == null || request == null) return;
        cliente.atualizarPerfil(
            request.nome(),
            request.telefone(),
            request.email(),
            request.cpf(),
            request.cidade(),
            request.estado()
        );
    }
}
