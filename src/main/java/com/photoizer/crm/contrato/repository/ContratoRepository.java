package com.photoizer.crm.contrato.repository;

import com.photoizer.crm.contrato.model.Contrato;
import com.photoizer.crm.contrato.model.StatusContrato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContratoRepository extends JpaRepository<Contrato, UUID>,
    JpaSpecificationExecutor<Contrato> {

    Optional<Contrato> findByTokenHash(String tokenHash);

    List<Contrato> findByStatus(StatusContrato status);
}
