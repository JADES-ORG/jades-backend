package com.upc.jadesbackend.repositories;

import com.upc.jadesbackend.entities.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PagoRepositorio extends JpaRepository<Pago,Long> {
    List<Pago> findAllByIdCuota_IdCredito_IdVenta_IdCliente_IdOrderByFechaPagoEfectivoDesc(Long idCliente);
}
