package com.upc.jadesbackend.repositories;

import com.upc.jadesbackend.entities.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClienteRepositorio  extends JpaRepository<Cliente, Long> {
    List<Cliente> findByIdTienda_IdOrderByIdAsc(Long tienda);
    List<Cliente> findByIdTienda_IdAndEstadoClienteOrderByIdAsc(Long tienda, String estado);
    Optional<Cliente> findByIdAndIdTienda_Id(Long id, Long tienda);
    boolean existsByIdTienda_IdAndDniClienteAndIdNot(Long tienda, String dni, Long id);
    List<Cliente> findAllByIdUsuario_IdOrderByIdAsc(Long idUsuario);
    Optional<Cliente> findByIdUsuario_IdAndIdTienda_Id(Long idUsuario, Long idTienda);

    List<Cliente> findAllByIdTienda_Id(Long idTienda);
}
