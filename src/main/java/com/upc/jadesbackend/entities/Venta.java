package com.upc.jadesbackend.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@Entity
@Table(name = "venta")
public class Venta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_venta", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente idCliente;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario_registro", nullable = false)
    private Usuario idUsuarioRegistro;

    @NotNull
    @ColumnDefault("CURRENT_DATE")
    @Column(name = "fecha_venta", nullable = false)
    private LocalDate fechaVenta;

    @Size(max = 20)
    @NotNull
    @Column(name = "modalidad_pago", nullable = false, length = 20)
    private String modalidadPago;

    @NotNull
    @Column(name = "numero_cuotas", nullable = false)
    private Integer numeroCuotas;

    @NotNull
    @Column(name = "total_venta", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalVenta;

    @Size(max = 3)
    @NotNull
    @ColumnDefault("'PEN'")
    @Column(name = "moneda_venta", nullable = false, length = 3)
    private String monedaVenta;

    @NotNull
    @ColumnDefault("LOCALTIME")
    @Column(name = "hora_venta", nullable = false)
    private LocalTime horaVenta;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "gracia_adicional", nullable = false)
    private Integer graciaAdicional;

    @Size(max = 10)
    @NotNull
    @ColumnDefault("'VIGENTE'")
    @Column(name = "estado_venta", nullable = false, length = 10)
    private String estadoVenta;


}