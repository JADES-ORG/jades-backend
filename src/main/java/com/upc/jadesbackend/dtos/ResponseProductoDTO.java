package com.upc.jadesbackend.dtos;
import com.upc.jadesbackend.entities.Producto;
public record ResponseProductoDTO(
        Long id,
        String nombreProducto,
        String descripcion,
        java.math.BigDecimal precioContado,
        java.math.BigDecimal precioCredito,
        java.math.BigDecimal precioContadoUsd,
        java.math.BigDecimal precioCreditoUsd,
        String tipoProducto,
        String proveedor,
        String marca,
        String unidadMedida,
        String imagenProducto,
        String modalidad,
        String estadoProducto)
{
    public static ResponseProductoDTO de(Producto e) {
        return new ResponseProductoDTO(
                e.getId(),
                e.getNombreProducto(),
                e.getDescripcion(),
                e.getPrecioContado(),
                e.getPrecioCredito(),
                e.getPrecioContadoUsd(),
                e.getPrecioCreditoUsd(),
                e.getTipoProducto(),
                e.getProveedor(),
                e.getMarca(),
                e.getUnidadMedida(),
                e.getImagenProducto(),
                e.getModalidad(),
                e.getEstadoProducto()
        );
    }
}