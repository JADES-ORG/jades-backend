package com.upc.jadesbackend.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Cotización informativa, sin IDs persistidos ni reserva de crédito. */
public record ResponseSimulacionVentaDTO(
        Long idCliente,
        String nombreCliente,
        String modalidadPago,
        Integer numeroCuotas,
        String moneda,
        LocalDate fechaVenta,
        LocalTime horaVenta,
        LocalDateTime fechaCorteAsignada,
        LocalDate fechaPagoAsignada,
        BigDecimal capitalVenta,
        BigDecimal capitalReservado,
        BigDecimal totalPagar,
        BigDecimal limiteCredito,
        BigDecimal creditoUtilizado,
        BigDecimal creditoDisponible,
        BigDecimal creditoDisponiblePosterior,
        List<ResponseVentaDTO.Detalle> detalles,
        ResponseCronogramaDTO cronograma,
        PagoUnico pagoUnico
) {
    public record PagoUnico(
            BigDecimal capital,
            long diasFinanciamiento,
            BigDecimal tasaEfectivaDiaria,
            BigDecimal tasaEfectivaPeriodo,
            BigDecimal interesCompensatorio,
            BigDecimal totalSinMora
    ) {}
}
