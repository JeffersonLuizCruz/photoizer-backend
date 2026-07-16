package com.photoizer.crm.cliente.service;

import com.photoizer.crm.cliente.exception.ClienteNaoEncontradoException;
import com.photoizer.crm.cliente.model.Cliente;
import com.photoizer.crm.cliente.repository.ClienteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Transactional
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    public Cliente criar(Cliente cliente) {
        return clienteRepository.save(cliente);
    }

    @Transactional(readOnly = true)
    public List<Cliente> listarTodos() {
        return clienteRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Cliente> buscarPorSearch(String search) {
        if (search == null || search.isBlank()) {
            return clienteRepository.findAll();
        }
        var exato = clienteRepository.findByTelefone(search);
        if (exato.isPresent()) {
            return List.of(exato.get());
        }
        var porTelefone = clienteRepository.findByTelefoneContaining(search);
        var porNome = clienteRepository.findByNomeContainingIgnoreCase(search);
        return Stream.concat(porTelefone.stream(), porNome.stream())
            .distinct()
            .toList();
    }

    @Transactional(readOnly = true)
    public Cliente buscarPorId(UUID id) {
        return clienteRepository.findById(id)
            .orElseThrow(() -> new ClienteNaoEncontradoException(id));
    }

    public Cliente atualizar(UUID id, Cliente dados) {
        var cliente = buscarPorId(id);
        cliente.setNome(dados.getNome());
        cliente.setTelefone(dados.getTelefone());
        cliente.setEmail(dados.getEmail());
        cliente.setCpf(dados.getCpf());
        cliente.setCidade(dados.getCidade());
        cliente.setEstado(dados.getEstado());
        cliente.setOrigem(dados.getOrigem());
        cliente.setObservacoes(dados.getObservacoes());
        return clienteRepository.save(cliente);
    }
}
