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
@Table(name = "cuota")
public class Cuota {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cuota", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_credito", nullable = false)
    private Credito idCredito;

    @NotNull
    @Column(name = "numero_cuota", nullable = false)
    private Integer numeroCuota;

    @NotNull
    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Size(max = 12)
    @NotNull
    @ColumnDefault("'PROGRAMADA'")
    @Column(name = "estado_cuota", nullable = false, length = 12)
    private String estadoCuota;

    @NotNull
    @Column(name = "importe", nullable = false, precision = 18, scale = 2)
    private BigDecimal importe;

    @NotNull
    @Column(name = "interes_cuota", nullable = false, precision = 18, scale = 2)
    private BigDecimal interesCuota;

    @NotNull
    @Column(name = "saldo_final", nullable = false, precision = 18, scale = 2)
    private BigDecimal saldoFinal;

    @NotNull
    @Column(name = "saldo_inicial", nullable = false, precision = 18, scale = 2)
    private BigDecimal saldoInicial;

    @NotNull
    @Column(name = "amortizacion", nullable = false, precision = 18, scale = 2)
    private BigDecimal amortizacion;


}