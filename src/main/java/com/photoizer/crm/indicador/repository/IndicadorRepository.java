package com.photoizer.crm.indicador.repository;

import com.photoizer.crm.indicador.model.Indicador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IndicadorRepository extends JpaRepository<Indicador, UUID> {

    Optional<Indicador> findByNomeAndTelefone(String nome, String telefone);

    @Query("SELECT i FROM Indicador i WHERE " +
           "LOWER(i.nome) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "i.telefone LIKE CONCAT('%', :search, '%') " +
           "ORDER BY i.nome ASC")
    List<Indicador> search(@Param("search") String search);
}
