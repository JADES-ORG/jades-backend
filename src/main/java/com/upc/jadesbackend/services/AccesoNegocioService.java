package com.upc.jadesbackend.services;
import com.upc.jadesbackend.entities.Tienda;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.*;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
public class AccesoNegocioService {
    private final UsuarioTiendaRepositorio vinculos;
    private final TiendaRepositorio tiendas;
    public void exigirParaCobro(Long idTienda, UsuarioPrincipal usuario) {
        if (usuario == null || !usuario.isEnabled() || !"ADMIN_NEGOCIO".equals(usuario.getRol())
                || !vinculos.tieneVinculoActivo(usuario.getId(), idTienda))
            throw new NegocioException(HttpStatus.FORBIDDEN, "No tienes acceso para cobrar en esta tienda");
    }
    public void exigirParaConsulta(Long idTienda, UsuarioPrincipal usuario) {
        if (usuario == null || !usuario.isEnabled() || !"ADMIN_NEGOCIO".equals(usuario.getRol())
                || !vinculos.tieneVinculoActivo(usuario.getId(), idTienda))
            throw new NegocioException(HttpStatus.FORBIDDEN, "No tienes acceso para consultar esta tienda");
    }
    public Tienda exigir(Long idTienda, UsuarioPrincipal usuario) {
        if (usuario == null || !usuario.isEnabled() || !"ADMIN_NEGOCIO".equals(usuario.getRol())
                || !vinculos.tieneAccesoActivo(usuario.getId(), idTienda))
            throw new NegocioException(HttpStatus.FORBIDDEN, "No tienes acceso activo a esta tienda");
        Tienda tienda = tiendas.bloquearParaVenta(idTienda).orElseThrow(() ->
                new NegocioException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));
        if (!"ACTIVO".equals(tienda.getEstadoTienda()))
            throw new NegocioException(HttpStatus.FORBIDDEN, "La tienda no está activa");
        return tienda;
    }
}
