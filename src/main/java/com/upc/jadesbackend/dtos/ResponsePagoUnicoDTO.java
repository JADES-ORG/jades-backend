package com.upc.jadesbackend.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

//HU-11: Respuesta del cálculo de pago único.
public record ResponsePagoUnicoDTO(
        Long idVenta,
        Long idCredito,
        Long idCuota,
        String codigoCredito,
        String moneda,
        LocalDate fechaVenta,
        LocalDateTime fechaCorteAsignada,
        LocalDate fechaPago,
        long diasFinanciamiento,
        BigDecimal capital,
        BigDecimal tasaEfectivaDiaria,
        BigDecimal tasaEfectivaPeriodo,
        BigDecimal interesCompensatorio,
        BigDecimal totalSinMora,
        String estadoCuota
) {}
