package com.upc.jadesbackend.util;

import com.upc.jadesbackend.util.CalculoFinancieroUtil.FilaCronograma;
import com.upc.jadesbackend.util.CalculoFinancieroUtil.ResultadoCronograma;
import com.upc.jadesbackend.util.CalculoFinancieroUtil.ResultadoMora;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class CalculoFinancieroUtilTest {

    private static void igual(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real),
                () -> "Esperado " + esperado + " pero fue " + real);
    }

    // ---------------- Tasas ----------------

    @Test
    @DisplayName("TNA 18% capitalización mensual -> TEM 1.5%")
    void tnaMensualATem() {
        BigDecimal tem = CalculoFinancieroUtil.tasaEfectivaMensual("NOMINAL", new BigDecimal("0.18"), "ANUAL", "MENSUAL");
        igual("0.015", tem);
    }

    @Test
    @DisplayName("Tasa ingresada como 18.0 (porcentaje) se auto-normaliza a 0.18 y produce la misma TEM 1.5%")
    void tasaAutoNormalizada() {
        BigDecimal tem = CalculoFinancieroUtil.tasaEfectivaMensual("NOMINAL", new BigDecimal("18.0"), "ANUAL", "MENSUAL");
        igual("0.015", tem);
        igual("0.18", CalculoFinancieroUtil.normalizarTasa(new BigDecimal("18.0")));
        igual("0.18", CalculoFinancieroUtil.normalizarTasa(new BigDecimal("0.18")));
    }

    @Test
    @DisplayName("TEA 12% -> TEM 0.9488793%")
    void teaATem() {
        BigDecimal tem = CalculoFinancieroUtil.tasaEfectivaMensual("EFECTIVA", new BigDecimal("0.12"), "ANUAL", null);
        igual("0.0094888", tem.setScale(7, java.math.RoundingMode.HALF_UP));
    }

    // ---------------- Caso de prueba N.° 2: venta del 15/09/2026 ----------------

    @Test
    @DisplayName("Caso 15/09: gracia total de 11 días capitaliza S/ 1,200.00 a S/ 1,206.57")
    void caso15SeptiembreGracia() {
        LocalDate venta = LocalDate.of(2026, 9, 15);
        LocalDate finGracia = LocalDate.of(2026, 9, 26);
        long dias = ChronoUnit.DAYS.between(venta, finGracia);
        assertEquals(11, dias);

        BigDecimal capital = CalculoFinancieroUtil.capitalizarGracia(new BigDecimal("1200.00"), new BigDecimal("0.015"), dias);
        igual("1206.57", capital);
    }

    @Test
    @DisplayName("Caso 15/09: cuota francesa base S/ 414.32")
    void caso15SeptiembreCuota() {
        BigDecimal cuota = CalculoFinancieroUtil.calcularCuotaFrancesa(new BigDecimal("1206.57"), new BigDecimal("0.015"), 3);
        igual("414.32", cuota);
    }

    @Test
    @DisplayName("Caso 15/09: cronograma oct-nov-dic con ajuste final por redondeo y saldo 0.00")
    void caso15SeptiembreCronograma() {
        ResultadoCronograma r = CalculoFinancieroUtil.generarCronograma(
                new BigDecimal("1206.57"), new BigDecimal("0.015"), 3, LocalDate.of(2026, 10, 26));

        assertEquals(3, r.filas().size());
        igual("414.32", r.cuotaBase());

        FilaCronograma c1 = r.filas().get(0);
        assertEquals(LocalDate.of(2026, 10, 26), c1.fechaVencimiento());
        igual("1206.57", c1.saldoInicial());
        igual("18.10", c1.interes());
        igual("396.22", c1.amortizacion());
        igual("414.32", c1.cuota());
        igual("810.35", c1.saldoFinal());

        FilaCronograma c2 = r.filas().get(1);
        assertEquals(LocalDate.of(2026, 11, 26), c2.fechaVencimiento());
        igual("810.35", c2.saldoInicial());
        igual("12.16", c2.interes());
        igual("402.16", c2.amortizacion());
        igual("414.32", c2.cuota());
        igual("408.19", c2.saldoFinal());

        FilaCronograma c3 = r.filas().get(2);
        assertEquals(LocalDate.of(2026, 12, 26), c3.fechaVencimiento());
        igual("408.19", c3.saldoInicial());
        igual("6.12", c3.interes());
        igual("408.19", c3.amortizacion());
        igual("414.31", c3.cuota());
        igual("0.00", c3.saldoFinal());

        igual("-0.01", r.ajusteUltimaCuota());
        igual("36.38", r.interesesTotales());
        igual("1242.95", r.totalFinanciamiento());
    }

    @Test
    @DisplayName("Sin gracia el capital no cambia")
    void sinGracia() {
        igual("1200.00", CalculoFinancieroUtil.capitalizarGracia(new BigDecimal("1200"), new BigDecimal("0.015"), 0));
    }

    @Test
    @DisplayName("Tasa cero reparte el capital en partes iguales")
    void tasaCero() {
        igual("400.00", CalculoFinancieroUtil.calcularCuotaFrancesa(new BigDecimal("1200"), BigDecimal.ZERO, 3));
    }

    // ---------------- Mora ----------------

    @Test
    @DisplayName("Pago dentro del plazo no genera mora")
    void pagoEnPlazoSinMora() {
        LocalDate venc = LocalDate.of(2026, 10, 26);
        long dias = CalculoFinancieroUtil.diasAtraso(venc, LocalDate.of(2026, 10, 25));
        assertEquals(0, dias);

        ResultadoMora m = CalculoFinancieroUtil.calcularMora(new BigDecimal("414.32"), new BigDecimal("0.001"), dias);
        igual("0.00", m.interesMoratorio());
        igual("414.32", m.totalExigible());
    }

    @Test
    @DisplayName("Pago con 10 días de atraso: mora compuesta diaria sobre la cuota vencida")
    void pagoAtrasado() {
        LocalDate venc = LocalDate.of(2026, 10, 26);
        long dias = CalculoFinancieroUtil.diasAtraso(venc, LocalDate.of(2026, 11, 5));
        assertEquals(10, dias);

        ResultadoMora m = CalculoFinancieroUtil.calcularMora(new BigDecimal("414.32"), new BigDecimal("0.001"), dias);
        igual("414.32", m.baseMora());
        igual("0.001", m.tasaMoraDiaria());
        assertEquals(10, m.diasAtraso());
        igual("4.16", m.interesMoratorio());
        igual("418.48", m.totalExigible());
    }
}
