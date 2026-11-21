package com.upc.jadesbackend.repositories;

import com.upc.jadesbackend.entities.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PagoRepositorio extends JpaRepository<Pago,Long> {
    List<Pago> findAllByIdCuota_IdCredito_IdVenta_IdCliente_IdOrderByFechaPagoEfectivoDesc(Long idCliente);

    Optional<Pago> findByIdCuota_Id(Long idCuota);
}
