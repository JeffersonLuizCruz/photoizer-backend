package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.ecommerce.exception.GaleriaNaoEncontradaException;
import com.photoizer.crm.ecommerce.exception.TokenExpiradoException;
import com.photoizer.crm.ecommerce.exception.FotoNaoEncontradaException;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * PATTERN: Facade Pattern
 * Fachada para consultas read-only da galeria. Centraliza as queries de
 * agendamento por token, valor unitário de foto extra e listagem de fotos
 * publicadas. Motivo: eliminar a duplicação de buscarAgendamentoPorToken()
 * que existia em EcommerceService e ComentarioService, e isolar a lógica
 * de resolução de token/validação de expiração em um único lugar.
 */
@Service
@Transactional(readOnly = true)
public class GaleriaQueryService {

    private final AgendamentoRepository agendamentoRepository;
    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final ConfiguracaoService configuracaoService;

    public GaleriaQueryService(AgendamentoRepository agendamentoRepository,
                               FotoEnsaioRepository fotoEnsaioRepository,
                               ConfiguracaoService configuracaoService) {
        this.agendamentoRepository = agendamentoRepository;
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.configuracaoService = configuracaoService;
    }

    public Agendamento buscarAgendamentoPorToken(UUID token) {
        var agendamento = agendamentoRepository.findByTokenGaleria(token)
            .orElseThrow(() -> new GaleriaNaoEncontradaException(token));
        if (agendamento.getTokenExpiracao() != null && agendamento.getTokenExpiracao().isBefore(LocalDateTime.now())) {
            throw new TokenExpiradoException("O link da galeria expirou. Solicite um novo link ao fotógrafo.");
        }
        return agendamento;
    }

    public BigDecimal getValorUnitarioFotoExtra() {
        return configuracaoService.getValorDecimal(ConfigKey.VALOR_FOTO_EXTRA);
    }

    public BigDecimal getValorUnitarioFotoExtra(UUID agendamentoId) {
        return agendamentoRepository.findById(agendamentoId)
            .map(agendamento -> agendamento.getPacote().getPrecoFotoExtra())
            .filter(preco -> preco != null && preco.signum() > 0)
            .orElseGet(this::getValorUnitarioFotoExtra);
    }

    public List<FotoEnsaio> listarFotosPublicadas(UUID token) {
        var agendamento = buscarAgendamentoPorToken(token);
        return fotoEnsaioRepository.findPublicadasVisiveisByAgendamentoId(agendamento.getId());
    }

    public List<FotoEnsaio> listarFotosPorAgendamento(UUID agendamentoId) {
        return fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId);
    }

    public Agendamento buscarAgendamentoPorId(UUID agendamentoId) {
        return agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new GaleriaNaoEncontradaException("Agendamento nao encontrado: " + agendamentoId));
    }

    public FotoEnsaio buscarFotoPorId(UUID fotoId) {
        return fotoEnsaioRepository.findById(fotoId)
            .orElseThrow(() -> new FotoNaoEncontradaException(fotoId));
    }

    public boolean isDownloadPermitido(FotoEnsaio foto) {
        return foto.isSelecionadaPacote() || foto.getStatus() == com.photoizer.crm.foto.model.StatusFoto.PAGA;
    }
}
