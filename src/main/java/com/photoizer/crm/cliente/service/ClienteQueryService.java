package com.photoizer.crm.cliente.service;

import com.photoizer.crm.cliente.repository.ClienteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Facade de leitura para dados de clientes.
 * Pattern: Query Service Facade — encapsula queries de clientes
 * para uso por outros módulos (dashboard) sem expor ClienteRepository.
 */
@Service
@Transactional(readOnly = true)
public class ClienteQueryService {

    private final ClienteRepository clienteRepository;

    public ClienteQueryService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    /**
     * Conta novos clientes cadastrados no período.
     */
    public long countNovosClientes(LocalDateTime inicio, LocalDateTime fim) {
        return clienteRepository.countByDataCadastroBetween(inicio, fim);
    }
}
