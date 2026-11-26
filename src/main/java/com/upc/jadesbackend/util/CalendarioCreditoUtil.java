package com.upc.jadesbackend.util;

import com.upc.jadesbackend.entities.CondicionesAplicadas;
import com.upc.jadesbackend.entities.Credito;
import com.upc.jadesbackend.entities.Cuota;
import com.upc.jadesbackend.entities.Venta;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    public static LocalDateTime corteHistoricoVenta(Venta venta, Credito credito) {
        if (venta.getFechaCorteAsignada() != null) return venta.getFechaCorteAsignada();
        return credito != null && credito.getFechaCorte() != null
                ? LocalDateTime.ofInstant(credito.getFechaCorte(), ZoneId.of("America/Lima")) : null;
    }

    public static YearMonth cicloHistoricoCuota(Cuota cuota) {
        Credito credito = cuota.getIdCredito();
        Venta venta = credito.getIdVenta();
        LocalDateTime corte = corteHistoricoVenta(venta, credito);
        if ("UNICO".equals(venta.getModalidadPago()) && corte != null) return YearMonth.from(corte);
        CondicionesAplicadas pacto = credito.getCondicionesAplicadas();
        YearMonth vencimiento = YearMonth.from(cuota.getFechaVencimiento());
        if (pacto != null && pacto.getDiaPago() != null && pacto.getDiaCorte() != null)
            return pacto.getDiaPago() <= pacto.getDiaCorte() ? vencimiento.minusMonths(1) : vencimiento;
        if (corte == null || credito.getFechaPrimerPago() == null) return null;
        int meses = "MENSUAL".equals(venta.getModalidadPago())
                ? 1 + (venta.getGraciaAdicional() == null ? 0 : venta.getGraciaAdicional()) : 0;
        YearMonth corteOriginal = YearMonth.from(corte);
        YearMonth pagoOriginal = YearMonth.from(credito.getFechaPrimerPago()).minusMonths(meses);
        if (pagoOriginal.equals(corteOriginal)) return vencimiento;
        if (pagoOriginal.equals(corteOriginal.plusMonths(1))) return vencimiento.minusMonths(1);
        return null;
    }

    public static LocalDateTime corteHistoricoCuota(Cuota cuota) {
        Credito credito = cuota.getIdCredito();
        Venta venta = credito.getIdVenta();
        LocalDateTime corte = corteHistoricoVenta(venta, credito);
        if ("UNICO".equals(venta.getModalidadPago())) return corte;
        CondicionesAplicadas pacto = credito.getCondicionesAplicadas();
        YearMonth ciclo = cicloHistoricoCuota(cuota);
        if (corte == null || pacto == null || pacto.getDiaCorte() == null || ciclo == null) return null;
        return LocalDateTime.of(fechaEnMes(ciclo, pacto.getDiaCorte()), corte.toLocalTime());
    }

    public static LocalDate fechaVencimiento(LocalDate fechaFinGracia, int numeroCuota, int diaPago) {
        return fechaEnMes(YearMonth.from(fechaFinGracia).plusMonths(numeroCuota), diaPago);
    }
}
