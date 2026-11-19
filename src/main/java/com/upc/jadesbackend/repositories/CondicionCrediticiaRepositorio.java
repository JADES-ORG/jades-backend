package com.upc.jadesbackend.repositories;

import com.upc.jadesbackend.entities.CondicionCredito;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

// HU-07
public interface CondicionCrediticiaRepositorio extends JpaRepository<CondicionCredito, Long> {
    Optional<CondicionCredito> findByIdCliente_Id(Long idCliente);
    boolean existsByIdCliente_Id(Long idCliente);

    // HU09: bloqueo exclusivo (SELECT ... FOR UPDATE) para serializar las ventas de un mismo cliente.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CondicionCredito c where c.idCliente.id = :idCliente")
    Optional<CondicionCredito> bloquearPorCliente(@Param("idCliente") Long idCliente);
}
