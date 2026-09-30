package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.model.Favorito;
import com.photoizer.crm.ecommerce.repository.FavoritoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * PATTERN: Facade Pattern
 * Encapsula operações de favoritos (wishlist) do cliente na galeria.
 * Motivo: separar a responsabilidade de favoritos do EcommerceService,
 * que acumula muitas responsabilidades ( carrinho, pagamento, download).
 */
@Service
@Transactional
public class FavoritoService {

    private final FavoritoRepository favoritoRepository;
    private final GaleriaQueryService galeriaQueryService;

    public FavoritoService(FavoritoRepository favoritoRepository,
                           GaleriaQueryService galeriaQueryService) {
        this.favoritoRepository = favoritoRepository;
        this.galeriaQueryService = galeriaQueryService;
    }

    public void adicionarFavorito(UUID token, UUID sessionId, UUID fotoId) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        if (favoritoRepository.findBySessionIdAndFotoId(sessionId, fotoId).isEmpty()) {
            favoritoRepository.save(Favorito.builder()
                .agendamentoId(agendamento.getId())
                .fotoId(fotoId)
                .sessionId(sessionId)
                .build());
        }
    }

    public void removerFavorito(UUID token, UUID sessionId, UUID fotoId) {
        galeriaQueryService.buscarAgendamentoPorToken(token);
        favoritoRepository.deleteBySessionIdAndFotoId(sessionId, fotoId);
    }

    @Transactional(readOnly = true)
    public List<UUID> listarFavoritos(UUID token, UUID sessionId) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        return favoritoRepository.findBySessionIdAndAgendamentoIdOrderByAuditInfoCreatedAtAsc(sessionId, agendamento.getId())
            .stream().map(Favorito::getFotoId).toList();
    }

    /**
     * Contagem anônima de curtidas por foto de um agendamento (sessões distintas).
     * Usada no painel admin para mostrar quais fotos o público mais curtiu.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Long> contarFavoritosPorFoto(UUID agendamentoId) {
        return favoritoRepository.contarFavoritosPorFoto(agendamentoId).stream()
            .collect(Collectors.toMap(
                row -> (UUID) row[0],
                row -> ((Number) row[1]).longValue()));
    }
}
