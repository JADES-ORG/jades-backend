package com.upc.jadesbackend.services;

import com.upc.jadesbackend.dtos.ResponseCalculoMoraDTO;
import com.upc.jadesbackend.entities.*;
import com.upc.jadesbackend.repositories.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientePortalServiceTest {
    @Test
    void pendienteIncluyeMoraSoloDeCuotasNoPagadas() {
        var clientes = mock(ClienteRepositorio.class); var ventas = mock(VentaRepositorio.class);
        var creditos = mock(CreditoRepositorio.class); var cuotas = mock(CuotaRepositorio.class);
        var pagos = mock(PagoRepositorio.class); var mora = mock(MoraService.class);
        var servicio = new ClientePortalService(clientes, ventas, creditos, cuotas, pagos, mora);
        Tienda tienda = new Tienda(); tienda.setId(1L);
        Cliente cliente = new Cliente(); cliente.setId(2L); cliente.setIdTienda(tienda);
        Venta venta = new Venta(); venta.setId(3L); venta.setMonedaVenta("PEN");
        Credito credito = new Credito(); credito.setId(4L); credito.setIdVenta(venta);
        Cuota pendiente = cuota(5L, credito); Cuota pagada = cuota(6L, credito);
        Pago pago = new Pago(); pago.setIdCuota(pagada); pago.setMoraPagada(new BigDecimal("1.00"));
        when(cuotas.findAllByIdCredito_IdVenta_IdCliente_IdOrderByFechaVencimientoAsc(2L)).thenReturn(List.of(pendiente, pagada));
        when(pagos.findAllByIdCuota_IdCredito_IdVenta_IdCliente_IdOrderByFechaPagoEfectivoDesc(2L)).thenReturn(List.of(pago));
        when(mora.construirDetalleMora(eq(pendiente), eq(credito), eq(venta), any())).thenReturn(
                new ResponseCalculoMoraDTO(5L, 1, 4L, null, 3L, pendiente.getFechaVencimiento(), LocalDate.now(),
                        1, true, false, new BigDecimal("110"), BigDecimal.ZERO, new BigDecimal("2"),
                        new BigDecimal("112"), "EN_MORA", "PEN"));
        var resultado = servicio.construirCuenta(cliente);
        assertEquals(0, new BigDecimal("112").compareTo(resultado.resumen().totalPendienteRegistrado()));
        assertEquals(0, new BigDecimal("2").compareTo(resultado.obligaciones().get(0).moraPendiente()));
        assertEquals(0, resultado.obligaciones().get(1).moraPendiente().signum());
        assertEquals(0, resultado.obligaciones().get(1).importePendiente().signum());
        verify(mora, never()).construirDetalleMora(eq(pagada), any(), any(), any());
    }

    private Cuota cuota(Long id, Credito credito) {
        Cuota cuota = new Cuota(); cuota.setId(id); cuota.setIdCredito(credito); cuota.setNumeroCuota(1);
        cuota.setFechaVencimiento(LocalDate.of(2020, 1, 1)); cuota.setAmortizacion(new BigDecimal("100"));
        cuota.setInteresCuota(new BigDecimal("10")); cuota.setImporte(new BigDecimal("110")); return cuota;
    }
}
