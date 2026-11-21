package com.upc.jadesbackend.repositories;

import com.upc.jadesbackend.entities.Credito;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CreditoRepositorio extends JpaRepository<Credito,Long> {
    List<Credito> findAllByIdVenta_IdCliente_Id(Long idCliente);

    Optional<Credito> findByIdVenta_Id(Long idVenta);

    // HU-15: Bloquea el crédito mientras se registra un pago.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Credito c where c.id = :idCredito")
    Optional<Credito> bloquearPorId(
            @Param("idCredito") Long idCredito
    );

}
