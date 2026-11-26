package com.upc.jadesbackend.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

// HU-15: Respuesta del registro de un pago.
public record ResponsePagoDTO(
        Long idPago,
        Long idCuota,
        Long idCredito,
        Long idVenta,
        String codigoCredito,
        String moneda,
        LocalDate fechaVencimiento,
        LocalDate fechaPagoEfectivo,
        LocalTime horaPagoEfectivo,
        int diasAtraso,
        String medioPago,
        BigDecimal moraPagada,
        BigDecimal interesPagado,
        BigDecimal capitalPagado,
        BigDecimal totalExigible,
        BigDecimal saldoCredito,
        String estadoCuota,
        String estadoCredito
) {}
