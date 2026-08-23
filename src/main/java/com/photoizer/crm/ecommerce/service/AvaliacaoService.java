package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.api.AvaliacaoRequest;
import com.photoizer.crm.ecommerce.api.AvaliacaoResponse;
import com.photoizer.crm.ecommerce.api.EcommerceMapper;
import com.photoizer.crm.ecommerce.model.Avaliacao;
import com.photoizer.crm.ecommerce.repository.AvaliacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * PATTERN: Service Layer
 * Encapsula a lógica de negócio de avaliações/depoimentos, removendo
 * o acesso direto ao repository no AvaliacaoController.
 */
@Service
@Transactional
public class AvaliacaoService {

    private final AvaliacaoRepository avaliacaoRepository;
    private final EcommerceMapper ecommerceMapper;

    public AvaliacaoService(AvaliacaoRepository avaliacaoRepository,
                            EcommerceMapper ecommerceMapper) {
        this.avaliacaoRepository = avaliacaoRepository;
        this.ecommerceMapper = ecommerceMapper;
    }

    public AvaliacaoResponse criar(AvaliacaoRequest request) {
        var avaliacao = Avaliacao.builder()
            .clienteId(request.clienteId())
            .agendamentoId(request.agendamentoId())
            .pacoteId(request.pacoteId())
            .pontuacao(request.pontuacao())
            .comentario(request.comentario())
            .depoimento(request.depoimento())
            .aprovado(false)
            .build();
        return ecommerceMapper.toAvaliacaoResponse(avaliacaoRepository.save(avaliacao));
    }

    @Transactional(readOnly = true)
    public List<AvaliacaoResponse> listarDepoimentos() {
        return avaliacaoRepository.findByAprovadoTrue().stream()
            .map(ecommerceMapper::toAvaliacaoResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<AvaliacaoResponse> listarPorCliente(UUID clienteId) {
        return avaliacaoRepository.findByClienteId(clienteId).stream()
            .map(ecommerceMapper::toAvaliacaoResponse)
            .toList();
    }
}
