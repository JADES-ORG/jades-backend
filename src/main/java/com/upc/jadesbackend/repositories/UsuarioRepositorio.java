package com.upc.jadesbackend.repositories;

import com.upc.jadesbackend.entities.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByCorreoUsuarioIgnoreCase(String correoUsuario);
    boolean existsByCorreoUsuarioIgnoreCase(String correoUsuario);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> bloquearParaRestablecerContrasena(@Param("id") Long id);
}
