package com.upc.jadesbackend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalendarioCreditoUtilTest {

    private static final LocalTime CORTE_18H = LocalTime.of(18, 0);

    @Test
    @DisplayName("Caso 15/09: corte 20/09, fin de gracia 26/09, vencimientos oct-nov-dic")
    void caso15Septiembre() {
        LocalDate corte = CalendarioCreditoUtil.fechaCorte(LocalDate.of(2026, 9, 15), LocalTime.of(10, 0), 20, CORTE_18H);
        assertEquals(LocalDate.of(2026, 9, 20), corte);

        LocalDate finGracia = CalendarioCreditoUtil.fechaFinGracia(corte, 20, 26, 0);
        assertEquals(LocalDate.of(2026, 9, 26), finGracia);

        assertEquals(LocalDate.of(2026, 10, 26), CalendarioCreditoUtil.fechaVencimiento(finGracia, 1, 26));
        assertEquals(LocalDate.of(2026, 11, 26), CalendarioCreditoUtil.fechaVencimiento(finGracia, 2, 26));
        assertEquals(LocalDate.of(2026, 12, 26), CalendarioCreditoUtil.fechaVencimiento(finGracia, 3, 26));
    }

    @Test
    @DisplayName("Venta después del día de corte pasa al corte del mes siguiente")
    void ventaDespuesDelCorte() {
        LocalDate corte = CalendarioCreditoUtil.fechaCorte(LocalDate.of(2026, 9, 21), LocalTime.of(10, 0), 20, CORTE_18H);
        assertEquals(LocalDate.of(2026, 10, 20), corte);
    }

    @Test
    @DisplayName("Venta el día de corte después de las 18:00 pasa al siguiente ciclo")
    void ventaDespuesDeLaHoraDeCorte() {
        assertEquals(LocalDate.of(2026, 9, 20),
                CalendarioCreditoUtil.fechaCorte(LocalDate.of(2026, 9, 20), LocalTime.of(18, 0), 20, CORTE_18H));
        assertEquals(LocalDate.of(2026, 10, 20),
                CalendarioCreditoUtil.fechaCorte(LocalDate.of(2026, 9, 20), LocalTime.of(18, 1), 20, CORTE_18H));
    }

    @Test
    @DisplayName("Si el día de pago es menor o igual al de corte, el pago cae el mes siguiente")
    void pagoMesSiguiente() {
        LocalDate finGracia = CalendarioCreditoUtil.fechaFinGracia(LocalDate.of(2026, 9, 25), 25, 5, 0);
        assertEquals(LocalDate.of(2026, 10, 5), finGracia);
    }

    @Test
    @DisplayName("Gracia adicional agrega meses al fin de gracia")
    void graciaAdicional() {
        LocalDate finGracia = CalendarioCreditoUtil.fechaFinGracia(LocalDate.of(2026, 9, 20), 20, 26, 1);
        assertEquals(LocalDate.of(2026, 10, 26), finGracia);
    }

    @Test
    @DisplayName("Día 31 en meses cortos usa el último día del mes")
    void dia31() {
        LocalDate finGracia = LocalDate.of(2026, 10, 31);
        assertEquals(LocalDate.of(2026, 11, 30), CalendarioCreditoUtil.fechaVencimiento(finGracia, 1, 31));
        assertEquals(LocalDate.of(2026, 12, 31), CalendarioCreditoUtil.fechaVencimiento(finGracia, 2, 31));
        assertEquals(LocalDate.of(2027, 2, 28), CalendarioCreditoUtil.fechaVencimiento(finGracia, 4, 31));
    }
}
