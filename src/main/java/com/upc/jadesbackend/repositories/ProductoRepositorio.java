package com.upc.jadesbackend.repositories;
import com.upc.jadesbackend.entities.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
public interface ProductoRepositorio extends JpaRepository<Producto, Long> {
    Optional<Producto> findByIdAndIdTienda_Id(Long id, Long tienda);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.id = :id and p.idTienda.id = :tienda")
    Optional<Producto> bloquearPorIdYTienda(@Param("id") Long id, @Param("tienda") Long tienda);
    boolean existsByImagenProductoAndIdNot(String imagenProducto, Long id);
    List<Producto> findByIdTienda_IdOrderByIdAsc(Long tienda);
    List<Producto> findByIdTienda_IdAndEstadoProductoOrderByIdAsc(Long tienda, String estado);
}