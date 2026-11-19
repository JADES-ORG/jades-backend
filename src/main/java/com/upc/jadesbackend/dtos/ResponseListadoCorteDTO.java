package com.upc.jadesbackend.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Listado de pago al corte (HU-13).
 * Corresponde al cliente, tienda y ciclo correctos.
 * Detalla las compras ordenadas cronológicamente, los intereses y el total exigible.
 */
public record ResponseListadoCorteDTO(
        Long idCliente,
        String nombreCliente,
        String documentoCliente,
        Long idTienda,
        String nombreTienda,
        String ciclo,
        LocalDate fechaCorteAnterior,
        LocalDate fechaCorteActual,
        LocalTime horaCorte,
        LocalDate fechaLimitePago,
        String moneda,
        ResumenCorte resumen,
        List<CompraCiclo> compras,
        List<ObligacionExigible> obligaciones
) {
    public record ResumenCorte(
            BigDecimal totalComprasCiclo,
            BigDecimal totalCapitalExigible,
            BigDecimal totalInteresesPeriodo,
            BigDecimal totalMoraAcumulada,
            BigDecimal totalExigible,
            int cantidadCompras,
            int cantidadObligaciones,
            int cantidadCuotasEnMora
    ) {}

    public record CompraCiclo(
            Long idVenta,
            LocalDate fechaVenta,
            LocalTime horaVenta,
            String modalidadPago,
            Integer numeroCuotas,
            BigDecimal totalVenta,
            Long idCredito,
            String codigoCredito,
            BigDecimal interesEstimado
    ) {}

    public record ObligacionExigible(
            Long idCuota,
            Long idCredito,
            String codigoCredito,
            Long idVenta,
            Integer numeroCuota,
            LocalDate fechaVencimiento,
            BigDecimal amortizacion,
            BigDecimal interes,
            BigDecimal importeCuota,
            long diasAtraso,
            BigDecimal moraCalculada,
            BigDecimal totalPagar,
            String estadoCuota,
            boolean enMora
    ) {}
}
