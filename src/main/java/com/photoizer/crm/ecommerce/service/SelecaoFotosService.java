package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.event.FotosSelecionadasEvent;
import com.photoizer.crm.ecommerce.exception.FotoJaSelecionadaException;
import com.photoizer.crm.ecommerce.exception.FotoNaoEncontradaException;
import com.photoizer.crm.ecommerce.exception.LimitePacoteExcedidoException;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * PATTERN: Facade Pattern
 * Encapsula todas as operações de seleção/desseleção de fotos para o pacote do cliente.
 * Motivo: extrair responsabilidade do EcommerceService (SRP), centralizando validações
 * de limite do pacote e bloqueio de remoção de fotos já baixadas.
 *
 * MODULITH: Escritas em FotoEnsaio são feitas via eventos.
 * O módulo ecommerce NÃO deve escrever diretamente em FotoEnsaio.
 */
@Service
@Transactional
public class SelecaoFotosService {

    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final GaleriaQueryService galeriaQueryService;
    private final ApplicationEventPublisher eventPublisher;

    public SelecaoFotosService(FotoEnsaioRepository fotoEnsaioRepository,
                               GaleriaQueryService galeriaQueryService,
                               ApplicationEventPublisher eventPublisher) {
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.galeriaQueryService = galeriaQueryService;
        this.eventPublisher = eventPublisher;
    }

    public List<FotoEnsaio> selecionarFotos(UUID token, List<UUID> fotoIds, boolean selecionada) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        var fotos = fotoEnsaioRepository.findAllById(fotoIds);

        if (selecionada) {
            validarLimitePacote(fotos, agendamento.getId(), agendamento.getPacote().getQuantidadeFotos());
        } else {
            bloquearRemocaoFotoBaixada(fotos, agendamento.getId());
        }

        var fotoIdsValidas = fotos.stream()
            .filter(f -> f.getAgendamentoId().equals(agendamento.getId()))
            .map(FotoEnsaio::getId)
            .toList();

        eventPublisher.publishEvent(new FotosSelecionadasEvent(
            agendamento.getId(), fotoIdsValidas, selecionada));

        return fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(agendamento.getId());
    }

    public FotoEnsaio overrideSelecao(UUID agendamentoId, UUID fotoId, boolean selecionada) {
        var foto = fotoEnsaioRepository.findById(fotoId)
            .orElseThrow(() -> new FotoNaoEncontradaException(fotoId));
        if (!foto.getAgendamentoId().equals(agendamentoId)) {
            throw new com.photoizer.crm.ecommerce.exception.FotoIndisponivelException(
                "Foto não pertence a este agendamento");
        }

        eventPublisher.publishEvent(new FotosSelecionadasEvent(
            agendamentoId, List.of(fotoId), selecionada));

        return fotoEnsaioRepository.findById(fotoId).orElse(foto);
    }

    private void validarLimitePacote(List<FotoEnsaio> fotos, UUID agendamentoId, int limitePacote) {
        var fotosSolicitadas = fotos.stream()
            .filter(f -> f.getAgendamentoId().equals(agendamentoId))
            .toList();
        var jaSelecionadas = fotoEnsaioRepository.countSelecionadasPacoteByAgendamentoId(agendamentoId);
        var novasSelecoes = fotosSolicitadas.stream().filter(f -> !f.isSelecionadaPacote()).count();
        if (jaSelecionadas + novasSelecoes > limitePacote) {
            throw new LimitePacoteExcedidoException(limitePacote);
        }
    }

    private void bloquearRemocaoFotoBaixada(List<FotoEnsaio> fotos, UUID agendamentoId) {
        var bloqueadas = fotos.stream()
            .filter(f -> f.getAgendamentoId().equals(agendamentoId))
            .filter(f -> f.isSelecionadaPacote() && f.getDataDownload() != null)
            .findAny();
        if (bloqueadas.isPresent()) {
            throw new FotoJaSelecionadaException("Foto já baixada não pode ser removida do pacote");
        }
    }
}
