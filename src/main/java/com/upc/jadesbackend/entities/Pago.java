package com.upc.jadesbackend.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "pago")
public class Pago {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago", nullable = false)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cuota", nullable = false)
    private Cuota idCuota;

    @NotNull
    @Column(name = "fecha_pago_efectivo", nullable = false)
    private LocalDate fechaPagoEfectivo;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "dias_atraso", nullable = false)
    private Integer diasAtraso;

    @Size(max = 20)
    @NotNull
    @Column(name = "medio_pago", nullable = false, length = 20)
    private String medioPago;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "mora_pagada", nullable = false, precision = 18, scale = 2)
    private BigDecimal moraPagada;

    @NotNull
    @Column(name = "total_exigible", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalExigible;

    @NotNull
    @Column(name = "capital_pagado", nullable = false, precision = 18, scale = 2)
    private BigDecimal capitalPagado;

    @NotNull
    @Column(name = "interes_pagado", nullable = false, precision = 18, scale = 2)
    private BigDecimal interesPagado;

    @NotNull
    @Column(name = "fecha_cancelacion", nullable = false)
    private LocalDate fechaCancelacion;

    @Size(max = 20)
    @NotNull
    @ColumnDefault("'REGISTRADO'")
    @Column(name = "estado_pago", nullable = false, length = 20)
    private String estadoPago;


}