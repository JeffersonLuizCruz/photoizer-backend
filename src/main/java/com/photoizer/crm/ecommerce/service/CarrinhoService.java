package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.api.CalculoCarrinhoResponse;
import com.photoizer.crm.ecommerce.api.CalculoItemResponse;
import com.photoizer.crm.ecommerce.event.ItemCarrinhoAdicionadoEvent;
import com.photoizer.crm.ecommerce.event.ItemCarrinhoRemovidoEvent;
import com.photoizer.crm.ecommerce.exception.CarrinhoVazioException;
import com.photoizer.crm.ecommerce.exception.FotoIndisponivelException;
import com.photoizer.crm.ecommerce.exception.FotoNaoEncontradaException;
import com.photoizer.crm.ecommerce.model.ItemCarrinho;
import com.photoizer.crm.ecommerce.repository.ItemCarrinhoRepository;
import com.photoizer.crm.foto.model.StatusFoto;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    public CarrinhoService(ItemCarrinhoRepository itemCarrinhoRepository,
                           GaleriaQueryService galeriaQueryService,
                           ApplicationEventPublisher eventPublisher) {
        this.itemCarrinhoRepository = itemCarrinhoRepository;
        this.galeriaQueryService = galeriaQueryService;
        this.eventPublisher = eventPublisher;
    }

    public void adicionarAoCarrinho(UUID token, UUID sessionId, UUID fotoId) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        var foto = galeriaQueryService.buscarFotoPorId(fotoId);

        if (!foto.getAgendamentoId().equals(agendamento.getId())) {
            throw new FotoIndisponivelException("Foto não pertence a esta galeria");
        }
        if (!foto.isVisivel() || foto.getStatus() != StatusFoto.PUBLICADA
            || foto.getCompraExtraId() != null
            || !galeriaQueryService.isFotoDisponivelParaCarrinho(foto)) {
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
            // O listener de extras anexa a foto à seleção ativa (se houver)
            // para que o estúdio a edite junto com o pacote.
            eventPublisher.publishEvent(new ItemCarrinhoAdicionadoEvent(agendamento.getId(), fotoId));
        }
    }

    public void removerDoCarrinho(UUID token, UUID sessionId, UUID fotoId) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        itemCarrinhoRepository.deleteBySessionIdAndAgendamentoIdAndFotoId(sessionId, agendamento.getId(), fotoId);
        // Remove também do snapshot da seleção ativa, quando a foto ainda não
        // foi editada. O listener valida a regra e bloqueia remoções tardias.
        eventPublisher.publishEvent(new ItemCarrinhoRemovidoEvent(agendamento.getId(), fotoId));
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
        var valorUnitarioPadrao = galeriaQueryService.getValorUnitarioFotoExtra(agendamento.getId());
        var quantidade = itensCarrinho.size();

        var itens = itensCarrinho.stream()
            .map(ItemCarrinho::getFotoId)
            .map(galeriaQueryService::buscarFotoPorId)
            .filter(foto -> foto.getAgendamentoId().equals(agendamento.getId()))
            .map(foto -> new CalculoItemResponse(
                foto.getId(),
                foto.getFileName(),
                galeriaQueryService.getValorUnitarioExtra(agendamento.getId(), foto.getId())))
            .toList();
        var subtotal = itens.stream()
            .map(CalculoItemResponse::valorUnitario)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

        return new CalculoCarrinhoResponse(itens, quantidade, valorUnitarioPadrao, subtotal, subtotal);
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
