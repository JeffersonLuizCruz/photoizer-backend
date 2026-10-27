package com.photoizer.crm.foto.repository;

import com.photoizer.crm.foto.model.ArmazenamentoSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ArmazenamentoSnapshotRepository
    extends JpaRepository<ArmazenamentoSnapshot, LocalDate> {

    List<ArmazenamentoSnapshot> findByDataGreaterThanEqualOrderByDataAsc(LocalDate inicio);
}
