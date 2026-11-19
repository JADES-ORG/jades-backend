package com.upc.jadesbackend.repositories;

import com.upc.jadesbackend.entities.Credito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CreditoRepositorio extends JpaRepository<Credito,Long> {
    List<Credito> findAllByIdVenta_IdCliente_Id(Long idCliente);

    Optional<Credito> findByIdVenta_Id(Long idVenta);
}
