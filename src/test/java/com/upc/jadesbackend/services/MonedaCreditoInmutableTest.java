package com.upc.jadesbackend.services;

import com.upc.jadesbackend.dtos.RequestCondicionCrediticiaDTO;
import com.upc.jadesbackend.entities.Cliente;
import com.upc.jadesbackend.entities.CondicionCredito;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.*;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonedaCreditoInmutableTest {
    @Mock CondicionCrediticiaRepositorio condiciones;
    @Mock TasaInteresRepositorio tasasInteres;
    @Mock TasaMoratoriaRepositorio tasasMoratorias;
    @Mock ClienteRepositorio clientes;
    @Mock AccesoNegocioService acceso;
    @Mock EvaluacionCreditoService evaluacion;
    @Mock UsuarioPrincipal principal;
    @InjectMocks CondicionCreditoService servicio;

    @Test
    void rechazaCambioDeMonedaAunqueNoExistaDeuda() {
        Cliente cliente = new Cliente();
        CondicionCredito existente = new CondicionCredito();
        existente.setMonedaCredito("PEN");
        when(clientes.findByIdAndIdTienda_Id(1L, 2L)).thenReturn(Optional.of(cliente));
        when(condiciones.bloquearPorCliente(2L)).thenReturn(Optional.of(existente));
        var dto = new RequestCondicionCrediticiaDTO(
                "USD", new BigDecimal("1000"), 12, 20, 26, null,
                "NOMINAL", new BigDecimal("0.18"), "ANUAL", "MENSUAL",
                "EFECTIVA", new BigDecimal("0.24"), "ANUAL", null);
        NegocioException error = assertThrows(NegocioException.class,
                () -> servicio.actualizar(1L, 2L, dto, principal));
        assertTrue(error.getMessage().contains("moneda"));
        verifyNoInteractions(evaluacion);
    }
}
