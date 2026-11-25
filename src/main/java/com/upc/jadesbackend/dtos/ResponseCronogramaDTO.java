package com.upc.jadesbackend.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Cronograma del crédito (HU-12): resumen financiero + tabla de cuotas. */
public record ResponseCronogramaDTO(
        Long idCredito,
        String codigoCredito,
        Long idVenta,
        String moneda,
        LocalDate fechaVenta,
        LocalDate fechaCorte,
        LocalDate fechaFinGracia,
        Long diasGracia,
        BigDecimal tasaEfectivaMensual,
        BigDecimal tasaEfectivaDiaria,
        BigDecimal capitalVenta,
        BigDecimal interesGracia,
        BigDecimal capitalCapitalizado,
        int numeroCuotas,
        BigDecimal cuotaBase,
        BigDecimal ajusteUltimaCuota,
        BigDecimal interesesTotales,
        BigDecimal totalFinanciamiento,
        List<CuotaCronograma> cuotas
) {
    public record CuotaCronograma(
            int numeroCuota,
            LocalDate fechaVencimiento,
            BigDecimal saldoInicial,
            BigDecimal interes,
            BigDecimal amortizacion,
            BigDecimal cuota,
            BigDecimal saldoFinal,
            String estado,
            Long idCuota,
            LocalDate fechaPagoEfectivo,
            BigDecimal capitalPagado,
            BigDecimal interesPagado,
            BigDecimal moraPagada,
            BigDecimal totalPagado) {}
}
