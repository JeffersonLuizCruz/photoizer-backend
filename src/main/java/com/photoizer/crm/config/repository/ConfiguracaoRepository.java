package com.photoizer.crm.config.repository;

import com.photoizer.crm.config.model.Configuracao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracaoRepository extends JpaRepository<Configuracao, String> {
}
