package com.upc.jadesbackend.repositories;
import com.upc.jadesbackend.entities.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface ProductoRepositorio extends JpaRepository<Producto, Long> {
    Optional<Producto> findByIdAndIdTienda_Id(Long id, Long tienda);
    List<Producto> findByIdTienda_IdOrderByIdAsc(Long tienda);
    List<Producto> findByIdTienda_IdAndEstadoProductoOrderByIdAsc(Long tienda, String estado);
}