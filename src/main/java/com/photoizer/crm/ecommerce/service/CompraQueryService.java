package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.api.AdminCompraDetalheResponse;
import com.photoizer.crm.ecommerce.api.AdminComprasRelatorioResponse;
import com.photoizer.crm.ecommerce.api.EcommerceMapper;
import com.photoizer.crm.ecommerce.exception.CompraNaoEncontradaException;
import com.photoizer.crm.ecommerce.model.CompraExtra;
import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import com.photoizer.crm.foto.api.FotoEnsaioResponse;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * PATTERN: Query Service Facade
 * Centraliza todas as queries de leitura de CompraExtra, isolando-as do
 * EcommerceService (orquestrador de writes). Motivo: aplicar CQRS leve
 * e reduzir o tamanho do EcommerceService, que acumulava 15+ metodos
 * de query misturados com operacoes de escrita.
 *
 * MODULITH: Este service pertence ao modulo ecommerce e acessa apenas
 * repositories do proprio modulo (CompraExtraRepository) ou via facade
 * read-only (GaleriaQueryService para resolucao de token).
 */
@Service
@Transactional(readOnly = true)
public class CompraQueryService {

    private final CompraExtraRepository compraExtraRepository;
    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final GaleriaQueryService galeriaQueryService;
    private final EcommerceMapper ecommerceMapper;

    public CompraQueryService(CompraExtraRepository compraExtraRepository,
                              FotoEnsaioRepository fotoEnsaioRepository,
                              GaleriaQueryService galeriaQueryService,
                              EcommerceMapper ecommerceMapper) {
        this.compraExtraRepository = compraExtraRepository;
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.galeriaQueryService = galeriaQueryService;
        this.ecommerceMapper = ecommerceMapper;
    }

    // ==================== Queries por Token (galeria do cliente) ====================

    public List<CompraExtra> listarComprasPorToken(UUID token) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        return compraExtraRepository.findByAgendamentoId(agendamento.getId());
    }

    public AdminCompraDetalheResponse buscarCompraDetalhePorToken(UUID token, UUID compraId) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        var compra = compraExtraRepository.findById(compraId)
            .orElseThrow(() -> new CompraNaoEncontradaException(compraId));
        if (!compra.getAgendamentoId().equals(agendamento.getId())) {
            throw new CompraNaoEncontradaException(compraId);
        }
        return buildDetalheResponse(compra, true);
    }

    public Path buscarComprovantePath(UUID token, UUID compraId) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        var compra = compraExtraRepository.findById(compraId)
            .orElseThrow(() -> new CompraNaoEncontradaException(compraId));
        if (!compra.getAgendamentoId().equals(agendamento.getId())) {
            throw new CompraNaoEncontradaException(compraId);
        }
        return compra.getUrlComprovante() != null ? Path.of(compra.getUrlComprovante()) : null;
    }

    // ==================== Queries Admin ====================

    public List<CompraExtra> listarComprasPorAgendamento(UUID agendamentoId) {
        return compraExtraRepository.findByAgendamentoId(agendamentoId);
    }

    public Path buscarComprovantePathPorId(UUID compraId) {
        var compra = compraExtraRepository.findById(compraId)
            .orElseThrow(() -> new CompraNaoEncontradaException(compraId));
        return compra.getUrlComprovante() != null ? Path.of(compra.getUrlComprovante()) : null;
    }

    public AdminCompraDetalheResponse buscarCompraDetalhe(UUID compraId) {
        var compra = compraExtraRepository.findById(compraId)
            .orElseThrow(() -> new CompraNaoEncontradaException(compraId));
        return buildDetalheResponse(compra, false);
    }

    public Page<CompraExtra> listarComprasPaginado(String status, LocalDateTime dataInicio,
                                                    LocalDateTime dataFim, int page, int perPage) {
        var pageable = PageRequest.of(page - 1, perPage, Sort.by(Sort.Direction.DESC, "auditInfo.createdAt"));
        if (status != null && !status.isBlank()) {
            var statusEnum = StatusCompraExtra.valueOf(status.toUpperCase());
            if (dataInicio != null && dataFim != null) {
                return compraExtraRepository.findByStatusAndPeriodo(statusEnum, dataInicio, dataFim, pageable);
            }
            return compraExtraRepository.findByStatus(statusEnum, pageable);
        }
        if (dataInicio != null && dataFim != null) {
            return compraExtraRepository.findByPeriodo(dataInicio, dataFim, pageable);
        }
        return compraExtraRepository.findAll(pageable);
    }

    public AdminComprasRelatorioResponse gerarRelatorio() {
        return new AdminComprasRelatorioResponse(
            (int) compraExtraRepository.count(),
            compraExtraRepository.countByStatus(StatusCompraExtra.AGUARDANDO_COMPROVANTE),
            compraExtraRepository.countByStatus(StatusCompraExtra.AGUARDANDO_CONFIRMACAO),
            compraExtraRepository.countByStatus(StatusCompraExtra.PAGA),
            compraExtraRepository.countByStatus(StatusCompraExtra.CANCELADA),
            compraExtraRepository.totalPorStatus(StatusCompraExtra.PAGA),
            compraExtraRepository.totalPorStatus(StatusCompraExtra.AGUARDANDO_COMPROVANTE)
                .add(compraExtraRepository.totalPorStatus(StatusCompraExtra.AGUARDANDO_CONFIRMACAO))
        );
    }

    // ==================== Helper ====================

    private AdminCompraDetalheResponse buildDetalheResponse(CompraExtra compra, boolean publico) {
        var fotos = fotoEnsaioRepository.findByCompraExtraId(compra.getId()).stream()
            .map(publico ? FotoEnsaioResponse::ofPublic : FotoEnsaioResponse::of)
            .toList();
        var comprovanteUrl = (!publico && compra.getUrlComprovante() != null)
            ? "/api/v1/admin/ecommerce/compras/" + compra.getId() + "/comprovante"
            : null;
        return new AdminCompraDetalheResponse(
            compra.getId(), compra.getAgendamentoId(), compra.getValorTotal(),
            compra.getStatus().name(), publico ? null : comprovanteUrl, compra.getDataPagamento(),
            compra.getQuantidadeFotos(),
            compra.getMetodoPagamento() != null ? compra.getMetodoPagamento().name() : null,
            fotos, compra.getAuditInfo().getCreatedAt(), compra.getAuditInfo().getUpdatedAt(),
            compra.getMotivoRecusa()
        );
    }
}
