package com.upc.jadesbackend.services;

import com.upc.jadesbackend.entities.Tienda;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.UsuarioTiendaRepositorio;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TiendaMisTiendasTest {
    private final UsuarioTiendaRepositorio vinculos = mock(UsuarioTiendaRepositorio.class);
    private final TiendaService servicio = new TiendaService(null, null, vinculos, null, null);

    private UsuarioPrincipal principal(String rol, boolean activo) {
        UsuarioPrincipal principal = mock(UsuarioPrincipal.class);
        when(principal.getId()).thenReturn(7L);
        when(principal.getRol()).thenReturn(rol);
        when(principal.isEnabled()).thenReturn(activo);
        return principal;
    }

    @Test
    void listaTiendasDelAdministradorAutenticado() {
        Tienda tienda = new Tienda();
        tienda.setId(12L);
        tienda.setNombreTienda("Bodega");
        tienda.setDocumentoTienda("12345678901");
        tienda.setEstadoTienda("ACTIVO");
        when(vinculos.listarTiendasVinculadas(7L)).thenReturn(List.of(tienda));

        var resultado = servicio.listarMisTiendas(principal("ADMIN_NEGOCIO", true));

        assertEquals(1, resultado.size());
        assertEquals(12L, resultado.get(0).idTienda());
        assertEquals("Bodega", resultado.get(0).nombreTienda());
        assertEquals("ACTIVO", resultado.get(0).estadoTienda());
        verify(vinculos).listarTiendasVinculadas(7L);
    }

    @Test
    void devuelveListaVaciaSinTiendasVinculadas() {
        when(vinculos.listarTiendasVinculadas(7L)).thenReturn(List.of());
        assertTrue(servicio.listarMisTiendas(principal("ADMIN_NEGOCIO", true)).isEmpty());
    }

    @Test
    void rechazaRolesNoAutorizadosYUsuariosInactivos() {
        for (var usuario : List.of(principal("CLIENTE", true),
                principal("ADMIN_SISTEMA", true), principal("ADMIN_NEGOCIO", false))) {
            var error = assertThrows(NegocioException.class, () -> servicio.listarMisTiendas(usuario));
            assertEquals(HttpStatus.FORBIDDEN, error.getEstado());
        }
        verifyNoInteractions(vinculos);
    }
}
