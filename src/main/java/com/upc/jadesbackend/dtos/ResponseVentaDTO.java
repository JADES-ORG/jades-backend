package com.upc.jadesbackend.dtos;

import com.upc.jadesbackend.entities.DetalleVenta;
import com.upc.jadesbackend.entities.Venta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

// Adicion de la HU08 (David K.)
public record ResponseVentaDTO(
        Long id,
        Long idCliente,
        String nombreCliente,
        LocalDate fechaVenta,
        LocalTime horaVenta,
        LocalDateTime fechaCorteAsignada,
        LocalDate fechaPagoAsignada,
        String modalidadPago,
        Integer numeroCuotas,
        Integer graciaAdicional,
        String monedaVenta,
        BigDecimal totalVenta,
        String estadoVenta,
        Long idUsuarioRegistro,
        List<Detalle> detalles
) {
    public record Detalle(
            Long id,
            Long idProducto,
            String nombreProducto,
            String unidadMedida,
            BigDecimal cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotal
    ) {
        public static Detalle de(DetalleVenta d) {
            return new Detalle(
                    d.getId(),
                    d.getIdProducto().getId(),
                    d.getIdProducto().getNombreProducto(),
                    d.getIdProducto().getUnidadMedida(),
                    d.getCantidad(),
                    d.getPrecioUnitario(),
                    d.getSubtotal());
        }
    }

    public static ResponseVentaDTO de(Venta v) {
        return new ResponseVentaDTO(
                v.getId(),
                v.getIdCliente().getId(),
                v.getIdCliente().getNombreCliente(),
                v.getFechaVenta(),
                v.getHoraVenta(),
                v.getFechaCorteAsignada(),
                v.getFechaPagoAsignada(),
                v.getModalidadPago(),
                v.getNumeroCuotas(),
                v.getGraciaAdicional(),
                v.getMonedaVenta(),
                v.getTotalVenta(),
                v.getEstadoVenta(),
                v.getIdUsuarioRegistro().getId(),
                v.getDetalles().stream().map(Detalle::de).toList());
    }
}
