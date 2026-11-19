package com.upc.jadesbackend.repositories;

import com.upc.jadesbackend.entities.TasaIntere;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TasaInteresRepositorio extends JpaRepository<TasaIntere, Long> {
    Optional<TasaIntere> findByIdCondicion_Id(Long idCondicion);
}
