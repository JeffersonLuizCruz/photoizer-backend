package com.photoizer.crm.cliente.service;

import com.photoizer.crm.cliente.api.dto.ClienteAdminResponse;
import com.photoizer.crm.cliente.api.dto.ClienteMapper;
import com.photoizer.crm.cliente.api.dto.CriarClienteRequest;
import com.photoizer.crm.cliente.api.dto.AtualizarClienteRequest;
import com.photoizer.crm.cliente.exception.ClienteNaoEncontradoException;
import com.photoizer.crm.cliente.model.Cliente;
import com.photoizer.crm.cliente.repository.ClienteRepository;
import com.photoizer.crm.cliente.repository.ClienteSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service para gerenciamento de clientes (admin).
 * Usa DTOs para contrato da API, nunca entidades JPA.
 */
@Service
@Transactional
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final ClienteMapper clienteMapper;

    public ClienteService(ClienteRepository clienteRepository, ClienteMapper clienteMapper) {
        this.clienteRepository = clienteRepository;
        this.clienteMapper = clienteMapper;
    }

    /**
     * Cria novo cliente a partir do DTO de requisição.
     * Padrão DTO Pattern - recebe DTO, retorna DTO.
     */
    public ClienteAdminResponse criar(CriarClienteRequest request) {
        var cliente = clienteMapper.toEntity(request);
        var salvo = clienteRepository.save(cliente);
        return clienteMapper.toAdminResponse(salvo);
    }

    /**
     * Lista clientes paginados com busca.
     * Usa Specification pattern para busca unificada.
     * Padrão Specification - elimina queries duplicadas e distinct() em memória.
     */
    @Transactional(readOnly = true)
    public Page<ClienteAdminResponse> listarPaginado(String search, Pageable pageable) {
        var spec = ClienteSpecification.buscarPorNomeOuTelefone(search);
        Page<Cliente> page;
        if (spec == null) {
            page = clienteRepository.findAll(pageable);
        } else {
            page = clienteRepository.findAll(spec, pageable);
        }
        return page.map(clienteMapper::toAdminResponse);
    }

    /**
     * Busca cliente por ID.
     * Retorna DTO de resposta admin.
     */
    @Transactional(readOnly = true)
    public ClienteAdminResponse buscarPorId(UUID id) {
        var cliente = clienteRepository.findById(id)
            .orElseThrow(() -> new ClienteNaoEncontradoException(id));
        return clienteMapper.toAdminResponse(cliente);
    }

    /**
     * Atualiza cliente existente.
     * Recebe DTO de requisição, retorna DTO de resposta.
     */
    public ClienteAdminResponse atualizar(UUID id, AtualizarClienteRequest request) {
        var cliente = clienteRepository.findById(id)
            .orElseThrow(() -> new ClienteNaoEncontradoException(id));
        clienteMapper.updateEntity(cliente, request);
        var atualizado = clienteRepository.save(cliente);
        return clienteMapper.toAdminResponse(atualizado);
    }

    /**
     * Exclui cliente por ID.
     */
    public void deletar(UUID id) {
        if (!clienteRepository.existsById(id)) {
            throw new ClienteNaoEncontradoException(id);
        }
        clienteRepository.deleteById(id);
    }

    /**
     * Busca cliente por telefone (usado internamente pelo módulo agenda).
     * Retorna entidade para uso interno entre módulos.
     */
    @Transactional(readOnly = true)
    public Cliente buscarPorTelefone(String telefone) {
        return clienteRepository.findByTelefone(telefone)
            .orElse(null);
    }

    /**
     * Busca cliente por email (usado internamente).
     * Retorna entidade para uso interno entre módulos.
     */
    @Transactional(readOnly = true)
    public Cliente buscarPorEmail(String email) {
        return clienteRepository.findByEmailIgnoreCase(email)
            .orElse(null);
    }

    /**
     * Salva cliente (usado internamente pelo módulo agenda).
     * Mantém compatibilidade com fluxos existentes.
     */
    public Cliente salvar(Cliente cliente) {
        return clienteRepository.save(cliente);
    }
}
