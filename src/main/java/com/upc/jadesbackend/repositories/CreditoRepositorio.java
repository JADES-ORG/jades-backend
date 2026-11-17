package com.upc.jadesbackend.repositories;

import com.upc.jadesbackend.entities.Credito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditoRepositorio extends JpaRepository<Credito,Long> {
    List<Credito> findAllByIdVenta_IdCliente_Id(Long idCliente);
}
