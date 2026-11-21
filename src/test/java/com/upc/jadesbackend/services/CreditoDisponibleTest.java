package com.upc.jadesbackend.services;

import com.upc.jadesbackend.entities.CondicionCredito;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.VentaRepositorio;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreditoDisponibleTest {
    @Test
    void utilizaSaldoActualYRecuperaDisponibilidadTrasPago() {
        var ventas = mock(VentaRepositorio.class);
        var servicio = new EvaluacionCreditoService(ventas);
        CondicionCredito condicion = new CondicionCredito();
        condicion.setLimiteCredito(new BigDecimal("1000")); condicion.setMonedaCredito("PEN");
        when(ventas.sumarCreditoUtilizadoPorCliente(1L)).thenReturn(new BigDecimal("900"), new BigDecimal("700"));
        assertThrows(NegocioException.class, () -> servicio.validarLimite(1L, condicion, new BigDecimal("200")));
        assertDoesNotThrow(() -> servicio.validarLimite(1L, condicion, new BigDecimal("200")));
    }
}
