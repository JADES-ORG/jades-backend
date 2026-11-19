package com.upc.jadesbackend.repositories;

import com.upc.jadesbackend.entities.TasaMoratoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TasaMoratoriaRepositorio extends JpaRepository<TasaMoratoria, Long> {
    Optional<TasaMoratoria> findByIdCondicion_Id(Long idCondicion);
}
