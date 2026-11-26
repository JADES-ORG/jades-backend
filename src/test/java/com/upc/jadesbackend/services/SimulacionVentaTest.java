package com.upc.jadesbackend.services;

import com.upc.jadesbackend.dtos.RequestVentaDTO;
import com.upc.jadesbackend.entities.*;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.*;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import com.upc.jadesbackend.util.CalculoFinancieroUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulacionVentaTest {
    private final VentaRepositorio ventas = mock(VentaRepositorio.class);
    private final ClienteRepositorio clientes = mock(ClienteRepositorio.class);
    private final ProductoRepositorio productos = mock(ProductoRepositorio.class);
    private final CondicionCrediticiaRepositorio condiciones = mock(CondicionCrediticiaRepositorio.class);
    private final TasaInteresRepositorio tasas = mock(TasaInteresRepositorio.class);
    private final TasaMoratoriaRepositorio moras = mock(TasaMoratoriaRepositorio.class);
    private final AccesoNegocioService acceso = mock(AccesoNegocioService.class);
    private final UsuarioPrincipal usuario = mock(UsuarioPrincipal.class);
    private final PagoUnicoService pagosUnicos = mock(PagoUnicoService.class);
    private final CronogramaService cronogramas = new CronogramaService(ventas, null, null, null,
            null, tasas, moras, null);
    private final VentaService servicio = new VentaService(ventas, clientes, productos, condiciones,
            null, null, acceso, new EvaluacionCreditoService(ventas), new AsignacionCicloService(),
            cronogramas, pagosUnicos, tasas, moras);

    @BeforeEach
    void preparar() {
        Tienda tienda = new Tienda(); tienda.setId(1L); tienda.setEstadoTienda("ACTIVO");
        Cliente cliente = new Cliente(); cliente.setId(2L); cliente.setIdTienda(tienda);
        cliente.setNombreCliente("Demo"); cliente.setEstadoCliente("ACTIVO");
        CondicionCredito condicion = new CondicionCredito(); condicion.setId(3L);
        condicion.setIdCliente(cliente); condicion.setLimiteCredito(new BigDecimal("2000.00"));
        condicion.setMonedaCredito("PEN"); condicion.setPlazoMaximo(6);
        condicion.setDiaCorte(20); condicion.setDiaPago(26); condicion.setHoraCorte(LocalTime.of(18,0));
        TasaInteres tasa = new TasaInteres(); tasa.setTipoTasa("EFECTIVA");
        tasa.setTasaInteres(new BigDecimal("0.025")); tasa.setPeriodoTasa("MENSUAL");
        TasaMoratoria mora = new TasaMoratoria(); mora.setTipoTasaMora("EFECTIVA");
        mora.setTasaMoratoria(new BigDecimal("0.012")); mora.setPeriodoTasaMora("MENSUAL");
        Producto producto = new Producto(); producto.setId(4L); producto.setNombreProducto("Demo");
        producto.setEstadoProducto("ACTIVO"); producto.setModalidad("AMBAS");
        producto.setUnidadMedida("UNIDAD"); producto.setPrecioCredito(new BigDecimal("100.00"));
        when(acceso.exigir(1L, usuario)).thenReturn(tienda);
        when(clientes.findByIdAndIdTienda_Id(2L, 1L)).thenReturn(Optional.of(cliente));
        when(condiciones.findByIdCliente_Id(2L)).thenReturn(Optional.of(condicion));
        when(tasas.findByIdCondicion_Id(3L)).thenReturn(Optional.of(tasa));
        when(moras.findByIdCondicion_Id(3L)).thenReturn(Optional.of(mora));
        when(productos.findByIdAndIdTienda_Id(4L, 1L)).thenReturn(Optional.of(producto));
        when(ventas.sumarCreditoUtilizadoPorCliente(2L)).thenReturn(new BigDecimal("500.00"));
    }

    private RequestVentaDTO solicitud(String modalidad, int cuotas) {
        return new RequestVentaDTO(2L, modalidad, cuotas, 0,
                List.of(new RequestVentaDTO.Detalle(4L, new BigDecimal("2"))));
    }

    @Test
    void mensualUsaCapitalCapitalizadoYNoPersiste() {
        var r = servicio.simular(1L, solicitud("MENSUAL", 3), usuario);
        assertEquals(new BigDecimal("200.00"), r.capitalVenta());
        assertEquals(3, r.cronograma().cuotas().size());
        assertEquals(r.cronograma().capitalCapitalizado(), r.capitalReservado());
        assertEquals(r.creditoDisponible().subtract(r.capitalReservado()), r.creditoDisponiblePosterior());
        assertNull(r.cronograma().idVenta());
        assertNull(r.pagoUnico());
        assertNull(r.detalles().getFirst().id());
        assertEquals(r.fechaPagoAsignada(), r.cronograma().cuotas().getFirst().fechaVencimiento());
        verify(ventas).sumarCreditoUtilizadoPorCliente(2L);
        verifyNoMoreInteractions(ventas);
        verifyNoInteractions(pagosUnicos);
    }

    @Test
    void unicoUsaCapitalOriginalYCalculaInteresHastaVencimiento() {
        var r = servicio.simular(1L, solicitud("UNICO", 1), usuario);
        var esperado = CalculoFinancieroUtil.calcularPagoUnico(r.capitalVenta(), r.fechaVenta(),
                r.fechaPagoAsignada(), "EFECTIVA", new BigDecimal("0.025"), "MENSUAL", null);
        assertEquals(esperado.totalPagar(), r.totalPagar());
        assertEquals(new BigDecimal("200.00"), r.capitalReservado());
        assertEquals(new BigDecimal("1300.00"), r.creditoDisponiblePosterior());
        assertNull(r.cronograma());
        assertNotNull(r.pagoUnico());
        verify(ventas).sumarCreditoUtilizadoPorCliente(2L);
        verifyNoMoreInteractions(ventas);
        verifyNoInteractions(pagosUnicos);
    }

    @Test
    void rechazaCreditoInsuficiente() {
        when(ventas.sumarCreditoUtilizadoPorCliente(2L)).thenReturn(new BigDecimal("1900.00"));
        assertThrows(NegocioException.class, () -> servicio.simular(1L, solicitud("UNICO", 1), usuario));
        verify(ventas).sumarCreditoUtilizadoPorCliente(2L);
        verifyNoMoreInteractions(ventas);
    }

    @Test
    void rechazaProductoDeOtraTienda() {
        when(productos.findByIdAndIdTienda_Id(4L, 1L)).thenReturn(Optional.empty());
        assertThrows(NegocioException.class, () -> servicio.simular(1L, solicitud("MENSUAL", 3), usuario));
        verifyNoInteractions(ventas);
    }
}
