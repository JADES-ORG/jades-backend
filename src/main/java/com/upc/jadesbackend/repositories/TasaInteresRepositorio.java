package com.upc.jadesbackend.repositories;

import com.upc.jadesbackend.entities.TasaInteres;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// HU-07 (David K.)
public interface TasaInteresRepositorio extends JpaRepository<TasaInteres, Long> {
    Optional<TasaInteres> findByIdCondicion_Id(Long idCondicion);
}
