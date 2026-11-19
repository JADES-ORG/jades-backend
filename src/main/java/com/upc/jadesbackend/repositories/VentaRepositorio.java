package com.upc.jadesbackend.repositories;

import com.upc.jadesbackend.entities.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface VentaRepositorio extends JpaRepository<Venta,Long> {
    List<Venta> findAllByIdCliente_IdOrderByFechaVentaDesc(Long idCliente);
    // Adiciones de la HU-08 (David K.)
    Optional<Venta> findByIdAndIdCliente_IdTienda_Id(Long id, Long tienda);
    List<Venta> findAllByIdCliente_IdTienda_IdOrderByFechaVentaDescHoraVentaDesc(Long tienda);
    List<Venta> findAllByIdCliente_IdAndIdCliente_IdTienda_IdOrderByFechaVentaDescHoraVentaDesc(Long idCliente, Long tienda);
    Optional<Venta> findByClaveIdempotencia(String claveIdempotencia);

    // Adiciones de la HU-09 (David K.)
    @Query("""
            select coalesce(sum(case when c.id is null then v.totalVenta else c.saldoCredito end), 0)
            from Venta v left join Credito c on c.idVenta = v
            where v.idCliente.id = :idCliente and v.estadoVenta = 'VIGENTE'
            """)
    BigDecimal sumarCreditoUtilizadoPorCliente(@Param("idCliente") Long idCliente);
}
