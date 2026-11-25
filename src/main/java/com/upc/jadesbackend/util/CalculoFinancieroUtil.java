package com.upc.jadesbackend.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Motor financiero de JADES.
 *
 * <p>Convenciones (Reporte Técnico, sección 2.1):
 * <ul>
 *   <li>Año comercial de 360 días y mes comercial de 30 días.</li>
 *   <li>Montos monetarios redondeados a 2 decimales (HALF_UP).</li>
 *   <li>Tasas manejadas con 12 decimales (cumple el mínimo de 7 y coincide con la escala de BD).</li>
 *   <li>Las tasas se reciben como fracción decimal: 18% = 0.18.</li>
 * </ul>
 *
 * <p><b>Tratamiento del redondeo (HU-12):</b> la cuota base se redondea a 2 decimales. En cada periodo el
 * interés se redondea a 2 decimales y la amortización es {@code cuota - interés}. En la última cuota la
 * amortización se fija igual al saldo inicial pendiente, por lo que la cuota final puede diferir en
 * céntimos de la cuota base. Esa diferencia se expone como {@code ajusteUltimaCuota} y garantiza que el
 * saldo final sea exactamente S/ 0.00.
 */
public final class CalculoFinancieroUtil {

    public static final int BASE_ANUAL = 360;
    public static final int BASE_MENSUAL = 30;
    public static final int DECIMALES_MONEDA = 2;
    public static final int DECIMALES_TASA = 12;

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    private CalculoFinancieroUtil() {
    }

    // ------------------------------------------------------------------
    // Resultados
    // ------------------------------------------------------------------

    public record FilaCronograma(
            int numeroCuota,
            LocalDate fechaVencimiento,
            BigDecimal saldoInicial,
            BigDecimal interes,
            BigDecimal amortizacion,
            BigDecimal cuota,
            BigDecimal saldoFinal) {
    }

    public record ResultadoCronograma(
            BigDecimal capitalFinanciado,
            BigDecimal tasaPeriodo,
            BigDecimal cuotaBase,
            BigDecimal ajusteUltimaCuota,
            BigDecimal interesesTotales,
            BigDecimal totalFinanciamiento,
            List<FilaCronograma> filas) {
    }

    public record ResultadoMora(
            BigDecimal baseMora,
            BigDecimal tasaMoraDiaria,
            long diasAtraso,
            BigDecimal interesMoratorio,
            BigDecimal totalExigible) {
    }

    public record ResultadoPagoUnico(
            BigDecimal capital,
            long diasFinanciamiento,
            BigDecimal tasaEfectivaDiaria,
            BigDecimal tasaEfectivaPeriodo,
            BigDecimal interesCompensatorio,
            BigDecimal totalPagar
    ) {}

    // ------------------------------------------------------------------
    // Tasas
    // ------------------------------------------------------------------

    public static BigDecimal normalizarTasa(BigDecimal valorTasa) {
        if (valorTasa == null || valorTasa.signum() <= 0) {
            throw new IllegalArgumentException("La tasa debe ser mayor a cero");
        }
        return valorTasa;
    }

    /** Días comerciales de un periodo: DIARIO=1, MENSUAL=30, ANUAL=360, etc. */
    public static int diasDelPeriodo(String periodo) {
        if (periodo == null || periodo.isBlank()) {
            throw new IllegalArgumentException("El periodo de la tasa es obligatorio");
        }
        String p = normalizar(periodo);
        if (p.startsWith("DIARI")) return 1;
        if (p.startsWith("QUINCENA")) return 15;
        if (p.startsWith("MENSUAL")) return 30;
        if (p.startsWith("BIMESTRAL")) return 60;
        if (p.startsWith("TRIMESTRAL")) return 90;
        if (p.startsWith("CUATRIMESTRAL")) return 120;
        if (p.startsWith("SEMESTRAL")) return 180;
        if (p.startsWith("ANUAL")) return BASE_ANUAL;
        throw new IllegalArgumentException("Periodo de tasa no soportado: " + periodo);
    }

    /**
     * Convierte la tasa pactada (nominal o efectiva) a una tasa efectiva para {@code diasObjetivo} días.
     * Recibe exclusivamente tasas en fracción decimal (18% = 0.18).
     *
     * <ul>
     *   <li>EFECTIVA: {@code (1 + i)^(dias / diasPeriodo) - 1}</li>
     *   <li>NOMINAL: {@code (1 + i * diasCap / diasPeriodo)^(dias / diasCap) - 1}</li>
     * </ul>
     */
    public static BigDecimal tasaEfectiva(String tipoTasa, BigDecimal valorTasa, String periodoTasa,
                                          String capitalizacion, int diasObjetivo) {
        BigDecimal tasa = normalizarTasa(valorTasa);
        int diasPeriodo = diasDelPeriodo(periodoTasa);
        String tipo = normalizar(tipoTasa);

        if (tipo.startsWith("EFECTIV")) {
            return convertirTasaEfectiva(tasa, diasPeriodo, diasObjetivo);
        }
        if (tipo.startsWith("NOMINAL")) {
            int diasCap = diasDelPeriodo(capitalizacion);
            BigDecimal tasaCap = tasa
                    .multiply(BigDecimal.valueOf(diasCap), MC)
                    .divide(BigDecimal.valueOf(diasPeriodo), MC);
            return convertirTasaEfectiva(tasaCap, diasCap, diasObjetivo);
        }
        throw new IllegalArgumentException("Tipo de tasa no soportado: " + tipoTasa);
    }

    public static BigDecimal tasaEfectivaMensual(String tipoTasa, BigDecimal valorTasa,
                                                 String periodoTasa, String capitalizacion) {
        return tasaEfectiva(tipoTasa, valorTasa, periodoTasa, capitalizacion, BASE_MENSUAL);
    }

    public static BigDecimal tasaEfectivaDiaria(String tipoTasa, BigDecimal valorTasa,
                                                String periodoTasa, String capitalizacion) {
        return tasaEfectiva(tipoTasa, valorTasa, periodoTasa, capitalizacion, 1);
    }

    /** Equivalencia de tasas efectivas: {@code (1 + i)^(diasDestino / diasOrigen) - 1}. */
    public static BigDecimal convertirTasaEfectiva(BigDecimal tasa, int diasOrigen, int diasDestino) {
        validarNoNegativo(tasa, "tasa");
        BigDecimal factor = potencia(BigDecimal.ONE.add(tasa), (double) diasDestino / diasOrigen);
        return factor.subtract(BigDecimal.ONE).setScale(DECIMALES_TASA, REDONDEO);
    }

    // ------------------------------------------------------------------
    // Gracia total y método francés (HU-12)
    // ------------------------------------------------------------------

    /** Capital post-gracia total: {@code C0 * (1 + TEM)^(dias / 30)}, equivalente a {@code C0 * (1 + TED)^dias}. */
    public static BigDecimal capitalizarGracia(BigDecimal capital, BigDecimal tasaMensual, long diasGracia) {
        validarNoNegativo(capital, "capital");
        if (diasGracia <= 0) {
            return redondearMoneda(capital);
        }
        BigDecimal factor = potencia(BigDecimal.ONE.add(tasaMensual), (double) diasGracia / BASE_MENSUAL);
        return redondearMoneda(capital.multiply(factor, MC));
    }

    /** Cuota francesa: {@code R = C * [TEP (1+TEP)^n] / [(1+TEP)^n - 1]}. */
    public static BigDecimal calcularCuotaFrancesa(BigDecimal capital, BigDecimal tasaPeriodo, int numeroCuotas) {
        validarNoNegativo(capital, "capital");
        if (numeroCuotas <= 0) {
            throw new IllegalArgumentException("El número de cuotas debe ser mayor a cero");
        }
        if (tasaPeriodo.signum() == 0) {
            return capital.divide(BigDecimal.valueOf(numeroCuotas), DECIMALES_MONEDA, REDONDEO);
        }
        BigDecimal factor = BigDecimal.ONE.add(tasaPeriodo).pow(numeroCuotas, MC);
        BigDecimal frc = tasaPeriodo.multiply(factor, MC).divide(factor.subtract(BigDecimal.ONE), MC);
        return redondearMoneda(capital.multiply(frc, MC));
    }

    /**
     * Genera el cronograma por método francés vencido simple ordinario.
     *
     * @param capitalFinanciado  capital ya capitalizado por la gracia (se redondea a 2 decimales)
     * @param tasaPeriodo        tasa efectiva del periodo de pago (TEM de 30 días)
     * @param numeroCuotas       número de cuotas
     * @param primerVencimiento  fecha de vencimiento de la cuota 1; las siguientes vencen mes a mes
     */
    public static ResultadoCronograma generarCronograma(BigDecimal capitalFinanciado, BigDecimal tasaPeriodo,
                                                        int numeroCuotas, LocalDate primerVencimiento) {
        BigDecimal capital = redondearMoneda(capitalFinanciado);
        BigDecimal cuotaBase = calcularCuotaFrancesa(capital, tasaPeriodo, numeroCuotas);

        List<FilaCronograma> filas = new ArrayList<>(numeroCuotas);
        BigDecimal saldo = capital;
        BigDecimal interesesTotales = BigDecimal.ZERO;
        BigDecimal totalCuotas = BigDecimal.ZERO;
        BigDecimal cuotaFinal = cuotaBase;

        for (int k = 1; k <= numeroCuotas; k++) {
            BigDecimal saldoInicial = saldo;
            BigDecimal interes = redondearMoneda(saldoInicial.multiply(tasaPeriodo, MC));
            BigDecimal amortizacion;
            BigDecimal cuota;

            if (k == numeroCuotas) {
                amortizacion = saldoInicial;          // ajuste final por redondeo
                cuota = amortizacion.add(interes);
                cuotaFinal = cuota;
            } else {
                cuota = cuotaBase;
                amortizacion = cuota.subtract(interes);
            }

            saldo = saldoInicial.subtract(amortizacion);
            interesesTotales = interesesTotales.add(interes);
            totalCuotas = totalCuotas.add(cuota);

            LocalDate vencimiento = primerVencimiento == null ? null : primerVencimiento.plusMonths(k - 1L);
            filas.add(new FilaCronograma(k, vencimiento, saldoInicial, interes, amortizacion, cuota, saldo));
        }

        return new ResultadoCronograma(
                capital,
                tasaPeriodo,
                cuotaBase,
                cuotaFinal.subtract(cuotaBase),
                interesesTotales,
                totalCuotas,
                Collections.unmodifiableList(filas));
    }

    // ------------------------------------------------------------------
    // Mora (HU-14)
    // ------------------------------------------------------------------

    /** Días de atraso; 0 si se paga en la fecha de vencimiento o antes. */
    public static long diasAtraso(LocalDate fechaVencimiento, LocalDate fechaPago) {
        return Math.max(0L, ChronoUnit.DAYS.between(fechaVencimiento, fechaPago));
    }

    /** Mora compuesta diaria sobre la cuota vencida: {@code cuota * ((1 + TEDm)^dias - 1)}. */
    public static ResultadoMora calcularMora(BigDecimal cuotaVencida, BigDecimal tasaMoraDiaria, long diasAtraso) {
        validarNoNegativo(cuotaVencida, "cuota vencida");
        BigDecimal base = redondearMoneda(cuotaVencida);
        validarNoNegativo(tasaMoraDiaria, "tasa moratoria diaria");
        BigDecimal tasa = tasaMoraDiaria;
        BigDecimal mora = BigDecimal.ZERO.setScale(DECIMALES_MONEDA);

        if (diasAtraso > 0 && tasa.signum() > 0) {
            BigDecimal factor = BigDecimal.ONE.add(tasa).pow((int) diasAtraso, MC);
            mora = redondearMoneda(base.multiply(factor.subtract(BigDecimal.ONE), MC));
        }
        return new ResultadoMora(base, tasa, Math.max(0L, diasAtraso), mora, base.add(mora));
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    public static BigDecimal redondearMoneda(BigDecimal valor) {
        return valor.setScale(DECIMALES_MONEDA, REDONDEO);
    }

    /** Potencia exacta para exponentes enteros; para fraccionarios usa doble precisión (~15 dígitos). */
    private static BigDecimal potencia(BigDecimal base, double exponente) {
        if (exponente == Math.rint(exponente) && Math.abs(exponente) <= 9999) {
            int e = (int) exponente;
            return e >= 0 ? base.pow(e, MC) : BigDecimal.ONE.divide(base.pow(-e, MC), MC);
        }
        return new BigDecimal(Math.pow(base.doubleValue(), exponente), MC);
    }

    private static String normalizar(String texto) {
        if (texto == null) return "";
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.trim().toUpperCase(Locale.ROOT);
    }

    private static void validarNoNegativo(BigDecimal valor, String nombre) {
        if (valor == null || valor.signum() < 0) {
            throw new IllegalArgumentException("El " + nombre + " debe ser un valor no negativo");
        }
    }

    /*
     * HU-11: Cálculo de deuda de pago único.
     * Permite calcular el interés compensatorio de una compra
     * utilizando tasas nominales o efectivas y el período
     * comprendido entre la compra y la fecha de pago.
     */
    public static ResultadoPagoUnico calcularPagoUnico(
            BigDecimal capital,
            LocalDate fechaVenta,
            LocalDate fechaPago,
            String tipoTasa,
            BigDecimal valorTasa,
            String periodoTasa,
            String capitalizacionTasa) {

        validarNoNegativo(capital, "capital");

        if (fechaVenta == null || fechaPago == null
                || fechaPago.isBefore(fechaVenta)) {
            throw new IllegalArgumentException("Las fechas de la operación son inválidas");
        }

        long dias = ChronoUnit.DAYS.between(fechaVenta, fechaPago);

        if (dias > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("El período de financiamiento es inválido");
        }

        BigDecimal ted = tasaEfectivaDiaria(
                tipoTasa,
                valorTasa,
                periodoTasa,
                capitalizacionTasa
        );

        BigDecimal tasaPeriodo = convertirTasaEfectiva(ted, 1, (int) dias);

        BigDecimal capitalRedondeado = redondearMoneda(capital);

        BigDecimal interes = redondearMoneda(
                capitalRedondeado.multiply(tasaPeriodo, MC)
        );

        return new ResultadoPagoUnico(
                capitalRedondeado,
                dias,
                ted,
                tasaPeriodo,
                interes,
                capitalRedondeado.add(interes)
        );
    }

}
