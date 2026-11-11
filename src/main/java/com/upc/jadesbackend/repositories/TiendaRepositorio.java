package com.upc.jadesbackend.repositories;

import com.upc.jadesbackend.entities.Tienda;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface TiendaRepositorio extends JpaRepository<Tienda, Long> {
    boolean existsByDocumentoTienda(String documentoTienda);

    // La baja de la tienda y la venta compiten por el mismo bloqueo de fila.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tienda t where t.id = :id")
    Optional<Tienda> bloquearParaBaja(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select t from Tienda t where t.id = :id")
    Optional<Tienda> bloquearParaVenta(@Param("id") Long id);
}
