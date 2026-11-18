package com.upc.jadesbackend.catalogo;

import com.upc.jadesbackend.dtos.*;
import com.upc.jadesbackend.entities.*;
import com.upc.jadesbackend.repositories.*;
import com.upc.jadesbackend.services.*;
import com.upc.jadesbackend.exceptions.NegocioException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GestionCatalogoTest {
    private RequestProductoDTO producto(String nombre, BigDecimal precio) {
        return new RequestProductoDTO(nombre, "Descripción", precio, new BigDecimal("120.00"),
                "PRODUCTO", null, null, "UNIDAD", null, "AMBAS");
    }

    @Test void rechazaPrecioCeroNegativoExcesoDecimalesYCamposObligatorios() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String precio : List.of("0", "-1", "0.001", "10000000000000000"))
                assertFalse(validator.validate(producto("Producto", new BigDecimal(precio))).isEmpty());
            assertFalse(validator.validate(producto(" ", BigDecimal.ONE)).isEmpty());
            assertTrue(validator.validate(producto("Producto", BigDecimal.ONE)).isEmpty());
        }
    }

    @Test void actualizacionConservaDescripcionYPrecioDeVentaAnterior() {
        var repo = mock(ProductoRepositorio.class);
        var acceso = mock(AccesoNegocioService.class);
        var detalles = mock(DetalleVentaRepositorio.class);
        var servicio = new ProductoService(repo, acceso, detalles);
        var p = new Producto(); p.setId(4L); p.setNombreProducto("Nombre anterior");
        p.setPrecioCredito(new BigDecimal("100.00"));
        var detalle = new DetalleVenta(); detalle.setIdProducto(p);
        detalle.setPrecioUnitario(new BigDecimal("95.00"));
        when(repo.findByIdAndIdTienda_Id(4L, 1L)).thenReturn(Optional.of(p));
        when(detalles.findByIdProducto_IdAndNombreProductoVentaIsNull(4L)).thenReturn(List.of(detalle));
        when(repo.save(p)).thenReturn(p);
        servicio.actualizar(1L, 4L, producto("Nombre nuevo", BigDecimal.TEN), null);
        assertEquals("Nombre anterior", detalle.getNombreProductoVenta());
        assertEquals(new BigDecimal("95.00"), detalle.getPrecioUnitario());
        assertEquals("Nombre nuevo", p.getNombreProducto());
    }

    @Test void desactivarClienteConservaRegistroYVinculoDeUsuario() {
        var repo = mock(ClienteRepositorio.class);
        var servicio = new ClienteService(repo, mock(AccesoNegocioService.class));
        var c = new Cliente(); c.setId(2L); var usuario = new Usuario(); c.setIdUsuario(usuario);
        when(repo.findByIdAndIdTienda_Id(2L, 1L)).thenReturn(Optional.of(c));
        when(repo.save(c)).thenReturn(c);
        assertEquals("INACTIVO", servicio.desactivar(1L, 2L, null).estadoCliente());
        assertSame(usuario, c.getIdUsuario());
        verify(repo, never()).delete(any(Cliente.class));
    }

    @Test void noPermiteAccesoDeOtraTienda() {
        var vinculos = mock(UsuarioTiendaRepositorio.class);
        var tiendas = mock(TiendaRepositorio.class);
        var u = new Usuario(); u.setId(8L); u.setActivo(true); u.setRol("ADMIN_NEGOCIO");
        var principal = new com.upc.jadesbackend.security.principal.UsuarioPrincipal(u);
        assertThrows(NegocioException.class, () -> new AccesoNegocioService(vinculos, tiendas).exigir(1L, principal));
        verifyNoInteractions(tiendas);
    }

    @Test void listadoPredeterminadoUsaSoloActivos() {
        var repo = mock(ProductoRepositorio.class);
        when(repo.findByIdTienda_IdAndEstadoProductoOrderByIdAsc(1L, "ACTIVO")).thenReturn(List.of());
        new ProductoService(repo, mock(AccesoNegocioService.class), mock(DetalleVentaRepositorio.class)).listar(1L, true, null);
        verify(repo).findByIdTienda_IdAndEstadoProductoOrderByIdAsc(1L, "ACTIVO");
        verify(repo, never()).findByIdTienda_IdOrderByIdAsc(any());
    }
    @Test void nuevasVentasRechazanInactivosYModalidadNoAdmitida() throws Exception {
        var tienda = new Tienda(); tienda.setId(1L); tienda.setEstadoTienda("ACTIVO");
        var c = new Cliente(); c.setIdTienda(tienda); c.setEstadoCliente("ACTIVO");
        var v = new Venta(); v.setIdCliente(c); v.setModalidadPago("MENSUAL");
        var p = new Producto(); p.setIdTienda(tienda); p.setEstadoProducto("INACTIVO");
        p.setModalidad("UNICO"); p.setNombreProducto("Original");
        var d = new DetalleVenta(); d.setIdVenta(v); d.setIdProducto(p);
        var callback = DetalleVenta.class.getDeclaredMethod("prepararNuevaVenta");
        callback.setAccessible(true);
        assertInstanceOf(IllegalStateException.class, assertThrows(java.lang.reflect.InvocationTargetException.class, () -> callback.invoke(d)).getCause());
        p.setEstadoProducto("ACTIVO");
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> callback.invoke(d));
        p.setModalidad("AMBAS");
        callback.invoke(d);
        assertEquals("Original", d.getNombreProductoVenta());
        c.setEstadoCliente("INACTIVO");
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> callback.invoke(d));
    }
}