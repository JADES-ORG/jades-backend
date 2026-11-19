package com.upc.jadesbackend.repositories;

import com.upc.jadesbackend.entities.Venta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VentaRepositorio extends JpaRepository<Venta,Long> {
    List<Venta> findAllByIdCliente_IdOrderByFechaVentaDesc(Long idCliente);

    List<Venta> findAllByIdCliente_IdOrderByFechaVentaAscHoraVentaAsc(Long idCliente);

    List<Venta> findAllByIdCliente_IdTienda_Id(Long idTienda);
}
