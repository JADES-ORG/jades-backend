package com.upc.jadesbackend.repositories;
import com.upc.jadesbackend.entities.DetalleVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface DetalleVentaRepositorio extends JpaRepository<DetalleVenta, Long> {
    List<DetalleVenta> findByIdProducto_IdAndNombreProductoVentaIsNull(Long producto);
}