package com.upc.jadesbackend.services;

import com.upc.jadesbackend.dtos.RequestRegistrarPagoDTO;
import com.upc.jadesbackend.entities.*;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.*;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PagoServiceTest {
    @Test
    void reintentoIgualNoRegistraOtroPagoYRechazaMontoDistinto() {
        var pagos = mock(PagoRepositorio.class); var cuotas = mock(CuotaRepositorio.class);
        var creditos = mock(CreditoRepositorio.class); var acceso = mock(AccesoNegocioService.class);
        var servicio = new PagoService(pagos, cuotas, creditos, mock(MoraService.class), acceso);
        Tienda tienda = new Tienda(); tienda.setId(1L);
        Cliente cliente = new Cliente(); cliente.setIdTienda(tienda);
        Venta venta = new Venta(); venta.setId(2L); venta.setIdCliente(cliente);
        Credito credito = new Credito(); credito.setId(3L); credito.setIdVenta(venta);
        Cuota cuota = new Cuota(); cuota.setId(4L); cuota.setIdCredito(credito);
        Pago pago = new Pago(); pago.setIdCuota(cuota); pago.setFechaPagoEfectivo(LocalDate.of(2026, 1, 1));
        pago.setMedioPago("EFECTIVO"); pago.setTotalExigible(new BigDecimal("110")); pago.setDiasAtraso(0);
        when(cuotas.findById(4L)).thenReturn(Optional.of(cuota));
        when(creditos.bloquearPorId(3L)).thenReturn(Optional.of(credito));
        when(pagos.findByIdCuota_Id(4L)).thenReturn(Optional.of(pago));
        var principal = mock(UsuarioPrincipal.class);
        assertFalse(servicio.registrar(4L, new RequestRegistrarPagoDTO(new BigDecimal("110.00"),
                LocalDate.of(2026, 1, 1), "efectivo"), principal).creado());
        assertThrows(NegocioException.class, () -> servicio.registrar(4L, new RequestRegistrarPagoDTO(
                new BigDecimal("111"), LocalDate.of(2026, 1, 1), "efectivo"), principal));
        verify(pagos, never()).saveAndFlush(any());
        verify(acceso, times(2)).exigirParaCobro(1L, principal);
    }
}
