package com.upc.jadesbackend.services;

import com.upc.jadesbackend.entities.Usuario;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.TiendaRepositorio;
import com.upc.jadesbackend.repositories.UsuarioTiendaRepositorio;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccesoCobroTest {
    @Test
    void cobroRequiereAdministradorActivoYVinculoActivo() {
        var vinculos = mock(UsuarioTiendaRepositorio.class);
        var servicio = new AccesoNegocioService(vinculos, mock(TiendaRepositorio.class));
        Usuario usuario = new Usuario(); usuario.setId(1L); usuario.setActivo(true); usuario.setRol("ADMIN_NEGOCIO");
        UsuarioPrincipal principal = new UsuarioPrincipal(usuario);
        when(vinculos.tieneVinculoActivo(1L, 2L)).thenReturn(true);
        assertDoesNotThrow(() -> servicio.exigirParaCobro(2L, principal));
        assertThrows(NegocioException.class, () -> servicio.exigirParaCobro(3L, principal));
        usuario.setRol("CLIENTE");
        assertThrows(NegocioException.class, () -> servicio.exigirParaCobro(2L, new UsuarioPrincipal(usuario)));
        assertThrows(NegocioException.class, () -> servicio.exigir(2L, principal));
    }
}
