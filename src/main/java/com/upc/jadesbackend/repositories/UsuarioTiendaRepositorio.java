package com.upc.jadesbackend.repositories;

import com.upc.jadesbackend.entities.UsuarioTienda;
import com.upc.jadesbackend.entities.Tienda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface UsuarioTiendaRepositorio extends JpaRepository<UsuarioTienda, Long> {
    @Query("""
        select ut from UsuarioTienda ut
        join fetch ut.idUsuario u
        join fetch ut.idTienda t
        where t.id in :tiendaIds and ut.estadoAcceso = 'ACTIVO'
          and u.rol = 'ADMIN_NEGOCIO'
        order by t.id, u.id
        """)
    List<UsuarioTienda> listarAdministradores(@Param("tiendaIds") List<Long> tiendaIds);

    @Query("""
        select count(ut) > 0 from UsuarioTienda ut
        where ut.idUsuario.id = :usuarioId and ut.idTienda.id = :tiendaId
          and ut.estadoAcceso = 'ACTIVO'
        """)
    boolean tieneVinculoActivo(@Param("usuarioId") Long usuarioId, @Param("tiendaId") Long tiendaId);

    @Query("""
        select count(ut) > 0 from UsuarioTienda ut
        where ut.idUsuario.id = :usuarioId
          and ut.idTienda.id = :tiendaId
          and ut.estadoAcceso = 'ACTIVO'
          and ut.idTienda.estadoTienda = 'ACTIVO'
        """)
    boolean tieneAccesoActivo(@Param("usuarioId") Long usuarioId, @Param("tiendaId") Long tiendaId);

    @Query("""
        select count(ut) > 0 from UsuarioTienda ut
        where ut.idUsuario.id = :usuarioId
          and ut.estadoAcceso = 'ACTIVO'
          and ut.idTienda.estadoTienda = 'ACTIVO'
        """)
    boolean tieneAlgunaTiendaActiva(@Param("usuarioId") Long usuarioId);

    @Query("""
        select ut.idTienda.id from UsuarioTienda ut
        where ut.idUsuario.id = :usuarioId
          and ut.estadoAcceso = 'ACTIVO'
          and ut.idTienda.estadoTienda = 'ACTIVO'
        order by ut.idTienda.id
        """)
    List<Long> idsTiendasActivas(@Param("usuarioId") Long usuarioId);

    @Query("""
        select distinct t from UsuarioTienda ut join ut.idTienda t
        where ut.idUsuario.id = :usuarioId
          and ut.estadoAcceso = 'ACTIVO'
          and t.estadoTienda = 'ACTIVO'
        order by t.id
        """)
    List<Tienda> listarTiendasActivas(@Param("usuarioId") Long usuarioId);

    @Query("""
        select distinct t from UsuarioTienda ut join ut.idTienda t
        where ut.idUsuario.id = :usuarioId
          and ut.estadoAcceso = 'ACTIVO'
        order by t.id
        """)
    List<Tienda> listarTiendasVinculadas(@Param("usuarioId") Long usuarioId);
}
