package com.upc.jadesbackend.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;

/**
 * Reglas de calendario del crédito (HU-12).
 *
 * <ol>
 *   <li><b>Fecha de corte:</b> el primer día de corte en o después de la venta. Si la venta ocurre el mismo día
 *       de corte pero después de la hora de corte, pasa al corte del mes siguiente.</li>
 *   <li><b>Fin de gracia:</b> el día de pago posterior a ese corte (mismo mes si {@code diaPago > diaCorte},
 *       si no, el mes siguiente). Si la venta tiene gracia adicional, se agregan esos meses.</li>
 *   <li><b>Vencimientos:</b> la cuota k vence el día de pago, k meses después del fin de gracia.</li>
 * </ol>
 * Si el día no existe en el mes (p. ej. 31 en noviembre), se usa el último día del mes.
 *
 * <p>Ejemplo (Caso N.° 2): venta 15/09, corte 20, pago 26 → corte 20/09, fin de gracia 26/09 (11 días),
 * vencimientos 26/10, 26/11 y 26/12.
 */
public final class CalendarioCreditoUtil {

    private CalendarioCreditoUtil() {
    }

    public static LocalDate fechaEnMes(YearMonth mes, int dia) {
        return mes.atDay(Math.min(dia, mes.lengthOfMonth()));
    }

    public static LocalDate fechaCorte(LocalDate fechaVenta, LocalTime horaVenta, int diaCorte, LocalTime horaCorte) {
        YearMonth mes = YearMonth.from(fechaVenta);
        LocalDate corte = fechaEnMes(mes, diaCorte);
        boolean despuesDeLaHora = fechaVenta.equals(corte)
                && horaVenta != null && horaCorte != null && horaVenta.isAfter(horaCorte);
        if (fechaVenta.isAfter(corte) || despuesDeLaHora) {
            corte = fechaEnMes(mes.plusMonths(1), diaCorte);
        }
        return corte;
    }

    public static LocalDate fechaFinGracia(LocalDate fechaCorte, int diaCorte, int diaPago, int mesesGraciaAdicional) {
        YearMonth mes = YearMonth.from(fechaCorte);
        if (diaPago <= diaCorte) {
            mes = mes.plusMonths(1);
        }
        return fechaEnMes(mes.plusMonths(Math.max(0, mesesGraciaAdicional)), diaPago);
    }

    public static LocalDate fechaVencimiento(LocalDate fechaFinGracia, int numeroCuota, int diaPago) {
        return fechaEnMes(YearMonth.from(fechaFinGracia).plusMonths(numeroCuota), diaPago);
    }
}
