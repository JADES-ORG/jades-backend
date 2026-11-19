package com.upc.jadesbackend.repositories;

import com.upc.jadesbackend.entities.CondicionCredito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CondicionCreditoRepositorio extends JpaRepository<CondicionCredito, Long> {
    Optional<CondicionCredito> findByIdCliente_Id(Long idCliente);
}
