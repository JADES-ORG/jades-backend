package com.upc.jadesbackend.dtos;

import java.math.BigDecimal;

/** Saldo calculado a partir de las ventas vigentes y sus créditos. */
public record ResponseCreditoDisponibleDTO(
        Long idCliente,
        String monedaCredito,
        BigDecimal limiteCredito,
        BigDecimal creditoUtilizado,
        BigDecimal creditoDisponible
) {}
