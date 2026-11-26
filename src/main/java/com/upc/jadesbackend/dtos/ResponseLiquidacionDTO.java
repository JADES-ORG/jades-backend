package com.upc.jadesbackend.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ResponseLiquidacionDTO(
        Long idCredito,
        Long idVenta,
        String moneda,
        LocalDate fechaPagoEfectivo,
        LocalTime horaPagoEfectivo,
        BigDecimal capital,
        BigDecimal interesCompensatorio,
        BigDecimal mora,
        BigDecimal totalPagar,
        BigDecimal saldoCapitalRestante,
        List<Detalle> obligaciones
) {
    public record Detalle(
            Long idCuota,
            Integer numeroCuota,
            LocalDate fechaVencimiento,
            BigDecimal capital,
            BigDecimal interesCompensatorio,
            BigDecimal mora,
            long diasAtraso,
            BigDecimal totalPagar
    ) {}
}
