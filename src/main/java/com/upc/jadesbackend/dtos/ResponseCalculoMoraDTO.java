package com.upc.jadesbackend.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Detalle del cálculo de mora de una cuota (HU-14).
 * Presenta la mora como concepto separado con su base, tasa, días y total exigible.
 */
public record ResponseCalculoMoraDTO(
        Long idCuota,
        Integer numeroCuota,
        Long idCredito,
        String codigoCredito,
        Long idVenta,
        LocalDate fechaVencimiento,
        LocalDate fechaEvaluacion,
        long diasAtraso,
        boolean enMora,
        boolean pagada,
        BigDecimal baseMora,
        BigDecimal tasaMoraDiaria,
        BigDecimal interesMoratorio,
        BigDecimal totalExigible,
        String estadoCuota,
        String moneda
) {}
