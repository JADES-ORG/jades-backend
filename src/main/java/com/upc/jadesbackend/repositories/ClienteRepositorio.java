package com.upc.jadesbackend.repositories;

import com.upc.jadesbackend.entities.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClienteRepositorio  extends JpaRepository<Cliente, Long> {
    List<Cliente> findByIdTienda_IdOrderByIdAsc(Long tienda);
    List<Cliente> findByIdTienda_IdAndEstadoClienteOrderByIdAsc(Long tienda, String estado);
    Optional<Cliente> findByIdAndIdTienda_Id(Long id, Long tienda);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from Cliente c where c.id = :id and c.idTienda.id = :tienda")
    Optional<Cliente> bloquearParaVenta(@Param("id") Long id, @Param("tienda") Long tienda);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cliente c where c.id = :id and c.idTienda.id = :tienda")
    Optional<Cliente> bloquearPorIdYTienda(@Param("id") Long id, @Param("tienda") Long tienda);
    boolean existsByIdTienda_IdAndDniClienteAndIdNot(Long tienda, String dni, Long id);
    List<Cliente> findAllByIdUsuario_IdOrderByIdAsc(Long idUsuario);
    Optional<Cliente> findByIdUsuario_IdAndIdTienda_Id(Long idUsuario, Long idTienda);

    List<Cliente> findAllByIdTienda_Id(Long idTienda);
}
