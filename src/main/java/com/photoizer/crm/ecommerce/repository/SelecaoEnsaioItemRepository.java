package com.photoizer.crm.ecommerce.repository;

import com.photoizer.crm.ecommerce.model.SelecaoEnsaioItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SelecaoEnsaioItemRepository extends JpaRepository<SelecaoEnsaioItem, UUID> {

    List<SelecaoEnsaioItem> findBySelecaoIdOrderByNumeroGaleriaAsc(UUID selecaoId);

    Optional<SelecaoEnsaioItem> findBySelecaoIdAndFotoId(UUID selecaoId, UUID fotoId);

    List<SelecaoEnsaioItem> findByFotoId(UUID fotoId);

    long countBySelecaoId(UUID selecaoId);

    long countBySelecaoIdAndEditadaTrue(UUID selecaoId);
}
