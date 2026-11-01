package com.upc.jadesbackend.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "producto")
public class Producto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tienda", nullable = false)
    private Tienda idTienda;

    @Size(max = 150)
    @NotNull
    @Column(name = "nombre_producto", nullable = false, length = 150)
    private String nombreProducto;

    @Size(max = 500)
    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @NotNull
    @Column(name = "precio_contado", nullable = false, precision = 18, scale = 2)
    private BigDecimal precioContado;

    @NotNull
    @Column(name = "precio_credito", nullable = false, precision = 18, scale = 2)
    private BigDecimal precioCredito;

    @Size(max = 10)
    @NotNull
    @Column(name = "tipo_producto", nullable = false, length = 10)
    private String tipoProducto;

    @Size(max = 150)
    @Column(name = "proveedor", length = 150)
    private String proveedor;

    @Size(max = 100)
    @Column(name = "marca", length = 100)
    private String marca;

    @Size(max = 30)
    @NotNull
    @Column(name = "unidad_medida", nullable = false, length = 30)
    private String unidadMedida;

    @Size(max = 500)
    @Column(name = "imagen_producto", length = 500)
    private String imagenProducto;

    @Size(max = 10)
    @NotNull
    @ColumnDefault("'AMBAS'")
    @Column(name = "modalidad", nullable = false, length = 10)
    private String modalidad;

    @Size(max = 10)
    @NotNull
    @ColumnDefault("'ACTIVO'")
    @Column(name = "estado_producto", nullable = false, length = 10)
    private String estadoProducto;


}