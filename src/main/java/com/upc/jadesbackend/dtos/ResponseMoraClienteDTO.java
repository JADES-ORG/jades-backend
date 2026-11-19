package com.upc.jadesbackend.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Consolidado de obligaciones y mora para un cliente (HU-14).
 */
public record ResponseMoraClienteDTO(
        Long idCliente,
        String nombreCliente,
        String documentoCliente,
        Long idTienda,
        LocalDate fechaEvaluacion,
        long cantidadCuotasEnMora,
        BigDecimal totalCuotasVencidas,
        BigDecimal totalMoraAcumulada,
        BigDecimal totalDeudaExigible,
        String moneda,
        List<ResponseCalculoMoraDTO> cuotasEnMora
) {}
