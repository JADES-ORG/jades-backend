package com.upc.jadesbackend.services;

import com.upc.jadesbackend.dtos.RequestSimularCronogramaDTO;
import com.upc.jadesbackend.dtos.ResponseCronogramaDTO;
import com.upc.jadesbackend.exceptions.NegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/** La simulación no usa repositorios, por eso el servicio se crea sin dependencias. */
class CronogramaServiceTest {

    private final CronogramaService servicio = new CronogramaService(null, null, null, null, null, null, null);

    private static void igual(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real),
                () -> "Esperado " + esperado + " pero fue " + real);
    }

    private static RequestSimularCronogramaDTO caso15Septiembre() {
        return new RequestSimularCronogramaDTO(
                new BigDecimal("1200.00"), "NOMINAL", new BigDecimal("0.18"), "ANUAL", "MENSUAL",
                3, LocalDate.of(2026, 9, 15), LocalTime.of(10, 0),
                20, LocalTime.of(18, 0), 26, 0, "PEN");
    }

    @Test
    @DisplayName("Caso N.° 2 completo: TNA 18% cap. mensual, 3 cuotas, corte 20, pago 26")
    void simularCaso15Septiembre() {
        ResponseCronogramaDTO r = servicio.simular(caso15Septiembre());

        assertEquals(LocalDate.of(2026, 9, 20), r.fechaCorte());
        assertEquals(LocalDate.of(2026, 9, 26), r.fechaFinGracia());
        assertEquals(11, r.diasGracia());
        igual("0.015", r.tasaEfectivaMensual());
        igual("1200.00", r.capitalVenta());
        igual("6.57", r.interesGracia());
        igual("1206.57", r.capitalCapitalizado());
        igual("414.32", r.cuotaBase());
        igual("-0.01", r.ajusteUltimaCuota());
        igual("36.38", r.interesesTotales());
        igual("1242.95", r.totalFinanciamiento());
        assertEquals(3, r.numeroCuotas());

        var c = r.cuotas();
        assertEquals(LocalDate.of(2026, 10, 26), c.get(0).fechaVencimiento());
        assertEquals(LocalDate.of(2026, 11, 26), c.get(1).fechaVencimiento());
        assertEquals(LocalDate.of(2026, 12, 26), c.get(2).fechaVencimiento());
        igual("414.32", c.get(0).cuota());
        igual("414.32", c.get(1).cuota());
        igual("414.31", c.get(2).cuota());
        igual("0.00", c.get(2).saldoFinal());
    }

    @Test
    @DisplayName("Simular ingresando la tasa como 18.0 (porcentaje) produce exactamente el mismo resultado que 0.18")
    void simularConTasaPorcentaje() {
        var s = new RequestSimularCronogramaDTO(
                new BigDecimal("1200.00"), "NOMINAL", new BigDecimal("18.0"), "ANUAL", "MENSUAL",
                3, LocalDate.of(2026, 9, 15), LocalTime.of(10, 0),
                20, LocalTime.of(18, 0), 26, 0, "PEN");
        ResponseCronogramaDTO r = servicio.simular(s);

        igual("0.015", r.tasaEfectivaMensual());
        igual("1206.57", r.capitalCapitalizado());
        igual("414.32", r.cuotaBase());
        igual("-0.01", r.ajusteUltimaCuota());
        igual("414.31", r.cuotas().get(2).cuota());
    }

    @Test
    @DisplayName("Tasa con periodo inválido devuelve error de negocio 400")
    void periodoInvalido() {
        var s = new RequestSimularCronogramaDTO(
                new BigDecimal("1200"), "NOMINAL", new BigDecimal("0.18"), "LUNAR", "MENSUAL",
                3, LocalDate.of(2026, 9, 15), null, 20, null, 26, 0, null);
        NegocioException e = assertThrows(NegocioException.class, () -> servicio.simular(s));
        assertEquals(400, e.getEstado().value());
    }
}
