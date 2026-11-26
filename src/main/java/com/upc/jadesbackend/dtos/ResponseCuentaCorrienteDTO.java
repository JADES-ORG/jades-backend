package com.upc.jadesbackend.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ResponseCuentaCorrienteDTO(
        Long idCliente,
        String nombreCliente,
        String documentoCliente,
        Long idTienda,
        String comercio,
        List<Compra> compras,
        List<Obligacion> obligaciones,
        List<PagoRegistrado> pagos,
        Resumen resumen
)
    // Records anidados - TEMPORAL
    // NOTITA: Podria evaluarse la idea de separar cada DTO independientemente si en caso se requieran usar en otros modulos o mejorar la organizaxion.
{
    public record Compra(
            Long idVenta,
            LocalDate fechaCompra,
            String modalidadPago,
            Integer numeroCuotas,
            BigDecimal total,
            String moneda,
            String estado,
            Long idCredito,
            String codigoCredito) {}

    public record Obligacion(
            Long idCuota,
            Long idCredito,
            Long idVenta,
            Integer numeroCuota,
            LocalDate fechaVencimiento,
            String estado, boolean vencida,
            BigDecimal capital,
            BigDecimal interesCompensatorio,
            BigDecimal importeCuota,
            BigDecimal importePendiente,
            BigDecimal moraPendiente,
            String moneda) {}

    public record PagoRegistrado(
            Long idPago,
            Long idCuota,
            LocalDate fechaPago,
            LocalTime horaPagoEfectivo,
            LocalDate fechaCancelacion,
            String medioPago,
            Integer diasAtraso,
            BigDecimal capitalPagado,
            BigDecimal interesPagado,
            BigDecimal moraPagada,
            BigDecimal totalExigible,
            String estado,
            Long idVenta,
            Integer numeroCuota,
            LocalDate fechaCompra,
            LocalDate fechaPagoPactada,
            String moneda,
            List<ResponseVentaDTO.Detalle> detalles) {}

    public record Resumen(
            BigDecimal totalCompras,
            BigDecimal capitalPendiente,
            BigDecimal interesPendiente,
            BigDecimal moraPagada,
            BigDecimal totalPendienteRegistrado,
            long cuotasPendientes,
            long cuotasVencidas) {}
}
