package com.upc.jadesbackend.repositories;

import com.upc.jadesbackend.entities.Cuota;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CuotaRepositorio extends JpaRepository<Cuota,Long> {
    List<Cuota> findAllByIdCredito_IdVenta_IdCliente_IdOrderByFechaVencimientoAsc(Long idCliente);

    List<Cuota> findAllByIdCredito_IdOrderByNumeroCuotaAsc(Long idCredito);
}
