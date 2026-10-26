package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.event.FotosSelecionadasEvent;
import com.photoizer.crm.ecommerce.exception.FotoIndisponivelException;
import com.photoizer.crm.ecommerce.exception.FotoJaSelecionadaException;
import com.photoizer.crm.ecommerce.exception.FotoNaoEncontradaException;
import com.photoizer.crm.ecommerce.exception.LimitePacoteExcedidoException;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    private static final Logger log = LoggerFactory.getLogger(SelecaoFotosService.class);

    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final GaleriaQueryService galeriaQueryService;
    private final GaleriaEnsaioService galeriaEnsaioService;
    private final SelecaoFotoValidator selecaoFotoValidator;
    private final ApplicationEventPublisher eventPublisher;

    public SelecaoFotosService(FotoEnsaioRepository fotoEnsaioRepository,
                               GaleriaQueryService galeriaQueryService,
                               GaleriaEnsaioService galeriaEnsaioService,
                               SelecaoFotoValidator selecaoFotoValidator,
                               ApplicationEventPublisher eventPublisher) {
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.galeriaQueryService = galeriaQueryService;
        this.galeriaEnsaioService = galeriaEnsaioService;
        this.selecaoFotoValidator = selecaoFotoValidator;
        this.eventPublisher = eventPublisher;
    }

    public List<FotoEnsaio> selecionarFotos(UUID token, List<UUID> fotoIds, boolean selecionada) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        galeriaEnsaioService.validarSelecaoPermitida(
            galeriaEnsaioService.obter(agendamento.getId()));

        var ids = new LinkedHashSet<>(fotoIds);
        var fotos = carregarFotos(ids);
        if (selecionada) {
            // Reenviar uma foto que já está no pacote é idempotente: não pode
            // ser bloqueado por ela já ter sido baixada (fluxo legado salva a
            // seleção inteira novamente).
            fotos.stream()
                .filter(foto -> !foto.isSelecionadaPacote())
                .forEach(foto -> selecaoFotoValidator.validar(foto, agendamento));
            validarLimitePacote(fotos, agendamento.getId(), agendamento.getPacote().getQuantidadeFotos());
        } else {
            bloquearRemocaoFotoBaixada(fotos, agendamento.getId());
        }

        eventPublisher.publishEvent(new FotosSelecionadasEvent(
            agendamento.getId(), List.copyOf(ids), selecionada));

        return fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(agendamento.getId());
    }

    public FotoEnsaio overrideSelecao(UUID agendamentoId, UUID fotoId, boolean selecionada) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorId(agendamentoId);
        galeriaEnsaioService.validarSelecaoPermitida(
            galeriaEnsaioService.obter(agendamentoId));
        var foto = fotoEnsaioRepository.findById(fotoId)
            .orElseThrow(() -> new FotoNaoEncontradaException(fotoId));
        if (!foto.getAgendamentoId().equals(agendamentoId)) {
            throw new FotoIndisponivelException("Foto não pertence a este agendamento");
        }

        if (selecionada) {
            if (!foto.isSelecionadaPacote()) {
                selecaoFotoValidator.validar(foto, agendamento);
                // O override manual não pode furar o limite contratado: se
                // passar do pacote, o cliente não consegue salvar/enviar a
                // seleção depois.
                if (agendamento.getPacote() != null) {
                    validarLimitePacote(
                        List.of(foto), agendamentoId, agendamento.getPacote().getQuantidadeFotos());
                }
            }
        } else if (foto.isSelecionadaPacote()) {
            bloquearRemocaoFotoBaixada(List.of(foto), agendamentoId);
            if (foto.getCompraExtraId() != null
                || foto.getStatus() == StatusFoto.PAGA
                || foto.getStatus() == StatusFoto.AGUARDANDO_COMPROVANTE
                || foto.getStatus() == StatusFoto.AGUARDANDO_CONFIRMACAO
                || foto.getEditadaPath() != null) {
                throw new FotoIndisponivelException(
                    "Foto vinculada a compra, edição ou pagamento não pode ser removida do pacote");
            }
        }

        eventPublisher.publishEvent(new FotosSelecionadasEvent(
            agendamentoId, List.of(fotoId), selecionada));

        var autenticacao = org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication();
        var totalPacote = fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId)
            .stream().filter(FotoEnsaio::isSelecionadaPacote).count();
        log.atInfo()
            .addKeyValue("event_action", "foto.override_selecao")
            .addKeyValue("agendamento_id", agendamentoId)
            .addKeyValue("foto_id", fotoId)
            .addKeyValue("selecionada", selecionada)
            .addKeyValue("quantidade_pacote", totalPacote)
            .addKeyValue("usuario", autenticacao != null && autenticacao.getName() != null
                ? autenticacao.getName() : "SYSTEM")
            .log("Override de seleção aplicado pelo staff");

        return fotoEnsaioRepository.findById(fotoId).orElse(foto);
    }

    private List<FotoEnsaio> carregarFotos(Set<UUID> fotoIds) {
        var fotos = fotoEnsaioRepository.findAllById(List.copyOf(fotoIds));
        if (fotos.size() != fotoIds.size()) {
            var encontrados = fotos.stream().map(FotoEnsaio::getId).toList();
            var ausente = fotoIds.stream()
                .filter(id -> !encontrados.contains(id))
                .findFirst()
                .orElseThrow();
            throw new FotoNaoEncontradaException(ausente);
        }
        return fotos;
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
