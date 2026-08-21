package com.photoizer.crm.auth.repository;

import com.photoizer.crm.auth.model.TokenBlocklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TokenBlocklistRepository extends JpaRepository<TokenBlocklist, UUID> {
    boolean existsByJti(String jti);
}
