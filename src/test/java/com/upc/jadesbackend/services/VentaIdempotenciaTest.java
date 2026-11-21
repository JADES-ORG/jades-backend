package com.upc.jadesbackend.services;

import com.upc.jadesbackend.dtos.RequestVentaDTO;
import com.upc.jadesbackend.entities.*;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.*;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VentaIdempotenciaTest {
    @Test
    void reintentoComparaTodosLosDatosDeLaSolicitud() {
        VentaRepositorio ventas = mock(VentaRepositorio.class);
        AccesoNegocioService acceso = mock(AccesoNegocioService.class);
        VentaService servicio = new VentaService(ventas, mock(ClienteRepositorio.class), mock(ProductoRepositorio.class),
                mock(CondicionCrediticiaRepositorio.class), mock(TiendaRepositorio.class), mock(UsuarioRepositorio.class),
                acceso, mock(EvaluacionCreditoService.class), mock(AsignacionCicloService.class),
                mock(CronogramaService.class), mock(PagoUnicoService.class));
        Tienda tienda = new Tienda(); tienda.setId(1L);
        Cliente cliente = new Cliente(); cliente.setId(2L); cliente.setIdTienda(tienda);
        Usuario usuario = new Usuario(); usuario.setId(3L);
        Producto producto = new Producto(); producto.setId(4L);
        Venta venta = new Venta(); venta.setId(5L); venta.setIdCliente(cliente); venta.setIdUsuarioRegistro(usuario);
        venta.setModalidadPago("MENSUAL"); venta.setNumeroCuotas(2); venta.setGraciaAdicional(0);
        DetalleVenta detalle = new DetalleVenta(); detalle.setIdProducto(producto); detalle.setCantidad(new BigDecimal("2.000"));
        venta.agregarDetalle(detalle);
        when(ventas.findByClaveIdempotencia("clave123")).thenReturn(Optional.of(venta));
        UsuarioPrincipal principal = mock(UsuarioPrincipal.class);
        RequestVentaDTO igual = solicitud("MENSUAL", 2, null, "2.0");
        assertEquals(5L, servicio.registrar(1L, igual, principal, "clave123").id());
        assertThrows(NegocioException.class, () -> servicio.registrar(1L, solicitud("UNICO", 1, 0, "2"), principal, "clave123"));
        assertThrows(NegocioException.class, () -> servicio.registrar(1L, solicitud("MENSUAL", 2, 1, "2"), principal, "clave123"));
        assertThrows(NegocioException.class, () -> servicio.registrar(1L, solicitud("MENSUAL", 2, 0, "3"), principal, "clave123"));
        verify(ventas, never()).saveAndFlush(any());
    }

    private RequestVentaDTO solicitud(String modalidad, int cuotas, Integer gracia, String cantidad) {
        return new RequestVentaDTO(2L, modalidad, cuotas, gracia,
                List.of(new RequestVentaDTO.Detalle(4L, new BigDecimal(cantidad))));
    }
}
