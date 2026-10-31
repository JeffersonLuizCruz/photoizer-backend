package com.photoizer.crm.ecommerce.repository;

import com.photoizer.crm.ecommerce.model.LeadPacote;
import com.photoizer.crm.ecommerce.model.StatusLead;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LeadPacoteRepository extends JpaRepository<LeadPacote, UUID> {

    Page<LeadPacote> findByStatus(StatusLead status, Pageable pageable);

    long countByStatus(StatusLead status);
}
