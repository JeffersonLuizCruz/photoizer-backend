package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.api.CalculoCarrinhoResponse;
import com.photoizer.crm.ecommerce.api.CalculoItemResponse;
import com.photoizer.crm.ecommerce.exception.CarrinhoVazioException;
import com.photoizer.crm.ecommerce.exception.FotoIndisponivelException;
import com.photoizer.crm.ecommerce.exception.FotoNaoEncontradaException;
import com.photoizer.crm.ecommerce.model.ItemCarrinho;
import com.photoizer.crm.ecommerce.repository.ItemCarrinhoRepository;
import com.photoizer.crm.foto.model.StatusFoto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * PATTERN: Facade Pattern
 * Encapsula todas as operações de carrinho (adicionar, remover, listar, contar, calcular)
 * em um único bean coeso.
 *
 * MODULITH: Usa GaleriaQueryService para queries read-only em FotoEnsaio.
 * O módulo ecommerce NÃO deve escrever diretamente em FotoEnsaio.
 */
@Service
@Transactional
public class CarrinhoService {

    private final ItemCarrinhoRepository itemCarrinhoRepository;
    private final GaleriaQueryService galeriaQueryService;

    public CarrinhoService(ItemCarrinhoRepository itemCarrinhoRepository,
                           GaleriaQueryService galeriaQueryService) {
        this.itemCarrinhoRepository = itemCarrinhoRepository;
        this.galeriaQueryService = galeriaQueryService;
    }

    public void adicionarAoCarrinho(UUID token, UUID sessionId, UUID fotoId) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        var foto = galeriaQueryService.buscarFotoPorId(fotoId);

        if (!foto.getAgendamentoId().equals(agendamento.getId())) {
            throw new FotoIndisponivelException("Foto não pertence a esta galeria");
        }
        if (!foto.isVisivel() || foto.getStatus() != StatusFoto.PUBLICADA) {
            throw new FotoIndisponivelException("Foto não está disponível para compra");
        }
        if (foto.isSelecionadaPacote()) {
            throw new FotoIndisponivelException("Foto já incluída no pacote não pode ser adicionada ao carrinho");
        }

        var jaExiste = itemCarrinhoRepository.findBySessionIdAndAgendamentoIdOrderByAuditInfoCreatedAtAsc(sessionId, agendamento.getId())
            .stream().anyMatch(item -> item.getFotoId().equals(fotoId));

        if (!jaExiste) {
            var item = ItemCarrinho.builder()
                .agendamentoId(agendamento.getId())
                .fotoId(fotoId)
                .sessionId(sessionId)
                .build();
            itemCarrinhoRepository.save(item);
        }
    }

    public void removerDoCarrinho(UUID token, UUID sessionId, UUID fotoId) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        itemCarrinhoRepository.deleteBySessionIdAndAgendamentoIdAndFotoId(sessionId, agendamento.getId(), fotoId);
    }

    @Transactional(readOnly = true)
    public List<ItemCarrinho> listarCarrinho(UUID token, UUID sessionId) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        return itemCarrinhoRepository.findBySessionIdAndAgendamentoIdOrderByAuditInfoCreatedAtAsc(sessionId, agendamento.getId());
    }

    @Transactional(readOnly = true)
    public int contarCarrinho(UUID token, UUID sessionId) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        return itemCarrinhoRepository.countBySessionIdAndAgendamentoId(sessionId, agendamento.getId());
    }

    @Transactional(readOnly = true)
    public CalculoCarrinhoResponse calcularCarrinho(UUID token, UUID sessionId) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        var itensCarrinho = itemCarrinhoRepository.findBySessionIdAndAgendamentoIdOrderByAuditInfoCreatedAtAsc(sessionId, agendamento.getId());
        var valorUnitario = galeriaQueryService.getValorUnitarioFotoExtra(agendamento.getId());
        var quantidade = itensCarrinho.size();
        var subtotal = valorUnitario.multiply(BigDecimal.valueOf(quantidade)).setScale(2, RoundingMode.HALF_UP);

        var itens = itensCarrinho.stream()
            .map(ItemCarrinho::getFotoId)
            .map(galeriaQueryService::buscarFotoPorId)
            .filter(foto -> foto.getAgendamentoId().equals(agendamento.getId()))
            .map(foto -> new CalculoItemResponse(foto.getId(), foto.getFileName(), valorUnitario))
            .toList();

        return new CalculoCarrinhoResponse(itens, quantidade, valorUnitario, subtotal, subtotal);
    }

    public List<UUID> obterFotoIdsCarrinho(UUID token, UUID sessionId) {
        return listarCarrinho(token, sessionId).stream()
            .map(ItemCarrinho::getFotoId)
            .toList();
    }

    public void limparCarrinho(UUID token, UUID sessionId) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        itemCarrinhoRepository.deleteBySessionIdAndAgendamentoId(sessionId, agendamento.getId());
    }
}
