package com.upc.jadesbackend.repositories;

import com.upc.jadesbackend.entities.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClienteRepositorio extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByIdUsuario_Id(Long idUsuario);

    List<Cliente> findAllByIdTienda_Id(Long idTienda);
}
