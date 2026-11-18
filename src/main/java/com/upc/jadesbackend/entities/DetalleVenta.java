package com.upc.jadesbackend.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "detalle_venta")
public class DetalleVenta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_venta", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_venta", nullable = false)
    private Venta idVenta;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto idProducto;

    @NotNull
    @Column(name = "cantidad", nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;

    @NotNull
    @Column(name = "precio_unitario", nullable = false, precision = 18, scale = 2)
    private BigDecimal precioUnitario;

    @NotNull
    @Column(name = "subtotal", nullable = false, precision = 18, scale = 2)
    private BigDecimal subtotal;


    // Snapshot: never resolve these values from the current catalog for historical sales.
    @Column(name="nombre_producto_venta", length=150)
    private String nombreProductoVenta;
    @Column(name="descripcion_producto_venta", length=500)
    private String descripcionProductoVenta;
    @Column(name="unidad_medida_venta", length=30)
    private String unidadMedidaVenta;
    @Column(name="tipo_producto_venta", length=10)
    private String tipoProductoVenta;

    @PrePersist
    private void prepararNuevaVenta() {
        if (idProducto == null || idVenta == null || idVenta.getIdCliente() == null)
            throw new IllegalStateException("La venta, cliente y producto son obligatorios");
        Cliente cliente = idVenta.getIdCliente();
        if (!"ACTIVO".equals(
                idProducto.getEstadoProducto())
                || !"ACTIVO".equals(cliente.getEstadoCliente())
                || !"ACTIVO".equals(idProducto.getIdTienda().getEstadoTienda())
        ) throw new IllegalStateException("La tienda, producto y cliente deben estar activos");

        if (!idProducto.getIdTienda().getId().equals(cliente.getIdTienda().getId()))
            throw new IllegalStateException("El producto y cliente deben pertenecer a la misma tienda");

        if (!"AMBAS".equals(idProducto.getModalidad()) && !idProducto.getModalidad().equals(idVenta.getModalidadPago()))
            throw new IllegalStateException("La modalidad no está permitida para este producto");
        capturarCatalogo();
    }
    public void capturarCatalogo() {
        nombreProductoVenta = idProducto.getNombreProducto();
        descripcionProductoVenta = idProducto.getDescripcion();
        unidadMedidaVenta = idProducto.getUnidadMedida();
        tipoProductoVenta = idProducto.getTipoProducto();
    }
}