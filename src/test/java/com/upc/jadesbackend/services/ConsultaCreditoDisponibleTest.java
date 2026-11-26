package com.upc.jadesbackend.services;

import com.upc.jadesbackend.entities.Cliente;
import com.upc.jadesbackend.entities.CondicionCredito;
import com.upc.jadesbackend.entities.TasaInteres;
import com.upc.jadesbackend.entities.TasaMoratoria;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.*;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsultaCreditoDisponibleTest {
    private final CondicionCrediticiaRepositorio condiciones = mock(CondicionCrediticiaRepositorio.class);
    private final TasaInteresRepositorio tasas = mock(TasaInteresRepositorio.class);
    private final TasaMoratoriaRepositorio moras = mock(TasaMoratoriaRepositorio.class);
    private final ClienteRepositorio clientes = mock(ClienteRepositorio.class);
    private final AccesoNegocioService acceso = mock(AccesoNegocioService.class);
    private final VentaRepositorio ventas = mock(VentaRepositorio.class);
    private final UsuarioPrincipal usuario = mock(UsuarioPrincipal.class);
    private final CondicionCreditoService servicio = new CondicionCreditoService(
            condiciones, tasas, moras, clientes, acceso, new EvaluacionCreditoService(ventas));

    @BeforeEach
    void preparar() {
        var cliente = new Cliente();
        cliente.setId(2L);
        cliente.setNombreCliente("Cliente Demo");
        var condicion = new CondicionCredito();
        condicion.setId(3L);
        condicion.setIdCliente(cliente);
        condicion.setLimiteCredito(new BigDecimal("2000.00"));
        condicion.setMonedaCredito("PEN");
        when(clientes.findByIdAndIdTienda_Id(2L, 1L)).thenReturn(Optional.of(cliente));
        when(condiciones.findByIdCliente_Id(2L)).thenReturn(Optional.of(condicion));
    }

    @Test
    void consultaActualizaElSaldoYNoPermiteDisponibilidadNegativa() {
        when(ventas.sumarCreditoUtilizadoPorCliente(2L)).thenReturn(
                new BigDecimal("500.00"), new BigDecimal("200.00"), new BigDecimal("2100.00"));
        var primera = servicio.consultarCreditoDisponible(1L, 2L, usuario);
        assertEquals(new BigDecimal("1500.00"), primera.creditoDisponible());
        assertEquals(new BigDecimal("500.00"), primera.creditoUtilizado());
        assertEquals(new BigDecimal("1800.00"), servicio.consultarCreditoDisponible(1L, 2L, usuario).creditoDisponible());
        assertEquals(0, servicio.consultarCreditoDisponible(1L, 2L, usuario).creditoDisponible().signum());
        verify(acceso, times(3)).exigir(1L, usuario);
    }

    @Test
    void consultaCondicionesIncluyeElMismoSaldo() {
        when(tasas.findByIdCondicion_Id(3L)).thenReturn(Optional.of(new TasaInteres()));
        when(moras.findByIdCondicion_Id(3L)).thenReturn(Optional.of(new TasaMoratoria()));
        when(ventas.sumarCreditoUtilizadoPorCliente(2L)).thenReturn(new BigDecimal("500.00"));
        var dto = servicio.consultar(1L, 2L, usuario);
        assertEquals(new BigDecimal("1500.00"), dto.creditoDisponible());
        assertEquals(new BigDecimal("500.00"), dto.creditoUtilizado());
    }

    @Test
    void rechazaClienteDeOtraTienda() {
        when(clientes.findByIdAndIdTienda_Id(2L, 9L)).thenReturn(Optional.empty());
        assertThrows(NegocioException.class, () -> servicio.consultarCreditoDisponible(9L, 2L, usuario));
        verifyNoInteractions(ventas);
    }
}
